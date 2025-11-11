(ns cripto-monitor.server
  "HTTP server configurado com Ring e Reitit"
  (:require [org.httpkit.server :as http-kit]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [reitit.ring :as reitit-ring]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [cripto-monitor.config :as config]
            [cripto-monitor.api.handlers :as handlers]
            [cripto-monitor.api.specs :as specs]
            [cripto-monitor.websocket.server :as websocket]))

(defn not-found-handler
  "404 handler"
  [request]
  (log/warn "Rota não encontrada:" (:uri request))
  {:status 404
   :headers {"Content-Type" "application/json"}
   :body {:error "not_found"
          :message "Endpoint não encontrado."}})

(defn method-not-allowed-handler
  "405 handler"
  [request]
  (log/warn "Método não permitido:" (:request-method request) (:uri request))
  {:status 405
   :headers {"Content-Type" "application/json"}
   :body {:error "method_not_allowed"
          :message (str "Método HTTP " (:request-method request) " não permitido para " (:uri request))}})

(defn wrap-debug-logging
  "Middleware para logging das requisições para debug."
  [handler]
  (fn [request]
    (log/info "Requisição recebida:" {:method (:request-method request)
                                  :uri (:uri request)
                                  :headers (select-keys (:headers request) ["content-type" "accept"])})
    (let [response (handler request)]
      (log/info "Resposta enviada:" {:status (:status response)
                                 :uri (:uri request)})
      response)))

(defn wrap-exceptions [handler]
  (fn [request]
    (try
      (handler request)
      (catch Exception e
        (log/error e "Exceção não tratada na requisição: " {:uri (:uri request)})
        {:status 500
         :headers {"Content-Type" "application/json"}
         :body {:error "internal_error"
                :message "Internal server error"
                :request-id (str (java.util.UUID/randomUUID))}}))))

(defn wrap-json-response [handler]
  (fn [request]
    (let [response (handler request)]
      (if (and (map? response) (:body response) (map? (:body response)))
        (-> response
            (update :body #(cheshire.core/generate-string %))
            (assoc-in [:headers "Content-Type"] "application/json"))
        response))))

(defn create-routes
  "Criando rotas com documentação OpenAPI"
  []
  [["" {:no-doc true}
    ["/swagger.json" {:get (swagger/create-swagger-handler
                            {:info specs/openapi-info
                             :servers specs/openapi-servers
                             :tags specs/openapi-tags})}]]

   ["/api-docs/*" {:no-doc true
                   :get (swagger-ui/create-swagger-ui-handler
                         {:url "/swagger.json"
                          :config {:validatorUrl nil}})}]

   ["/api"
    ["/health" {:get {:handler handlers/health-check
                      :summary "Verificação de saúde do sistema"
                      :description "Verifica o status do banco de dados, collector e outros componentes"
                      :tags ["health"]
                      :responses {200 {:description "Sistema saudável"
                                       :content {"application/json" {:schema {:allOf [specs/success-response-schema
                                                                                       {:properties {:data specs/health-schema}}]}}}}
                                  503 {:description "Sistema com problemas"
                                       :content {"application/json" {:schema specs/error-response-schema}}}}}
                :name ::health}]

    ["/coins" {:get {:handler handlers/get-all-coins
                     :summary "Lista todas as moedas"
                     :description "Retorna lista de todas as criptomoedas disponíveis no sistema"
                     :tags ["coins"]
                     :responses {200 {:description "Lista de moedas"
                                      :content {"application/json" {:schema {:allOf [specs/success-response-schema
                                                                                      {:properties {:data {:type "array"
                                                                                                           :items specs/coin-schema}}}]}}}}
                                 500 {:description "Erro interno"
                                      :content {"application/json" {:schema specs/error-response-schema}}}}}
               :name ::all-coins}]

    ["/coins/:symbol" {:get {:handler handlers/get-coin-by-symbol
                             :summary "Busca moeda por símbolo"
                             :description "Retorna informações de uma moeda específica pelo símbolo"
                             :tags ["coins"]
                             :parameters [specs/symbol-path-param]
                             :responses {200 {:description "Moeda encontrada"
                                              :content {"application/json" {:schema {:allOf [specs/success-response-schema
                                                                                              {:properties {:data specs/coin-schema}}]}}}}
                                         404 {:description "Moeda não encontrada"
                                              :content {"application/json" {:schema specs/error-response-schema}}}
                                         500 {:description "Erro interno"
                                              :content {"application/json" {:schema specs/error-response-schema}}}}}
                       :name ::coin-by-symbol}]

    ["/search/coins" {:get {:handler handlers/search-coins
                            :summary "Busca moedas"
                            :description "Busca moedas por nome ou símbolo"
                            :tags ["search"]
                            :parameters [specs/search-query-param]
                            :responses {200 {:description "Resultados da busca"
                                             :content {"application/json" {:schema specs/success-response-schema}}}
                                        400 {:description "Parâmetros inválidos"
                                             :content {"application/json" {:schema specs/error-response-schema}}}
                                        500 {:description "Erro interno"
                                             :content {"application/json" {:schema specs/error-response-schema}}}}}
                      :name ::search-coins}]

    ["/prices"
     ["/current" {:get {:handler handlers/get-current-prices
                        :summary "Preços atuais"
                        :description "Retorna os preços atuais de todas as moedas"
                        :tags ["prices"]
                        :responses {200 {:description "Preços atuais"
                                         :content {"application/json" {:schema {:allOf [specs/success-response-schema
                                                                                         {:properties {:data {:type "array"
                                                                                                              :items specs/price-schema}}}]}}}}
                                    500 {:description "Erro interno"
                                         :content {"application/json" {:schema specs/error-response-schema}}}}}
                  :name ::current-prices}]

     ["/current/:symbol" {:get {:handler handlers/get-current-price-by-symbol
                                :summary "Preço atual por símbolo"
                                :description "Retorna o preço atual de uma moeda específica"
                                :tags ["prices"]
                                :parameters [specs/symbol-path-param]
                                :responses {200 {:description "Preço atual"
                                                 :content {"application/json" {:schema {:allOf [specs/success-response-schema
                                                                                                 {:properties {:data specs/price-schema}}]}}}}
                                            404 {:description "Moeda não encontrada"
                                                 :content {"application/json" {:schema specs/error-response-schema}}}
                                            500 {:description "Erro interno"
                                                 :content {"application/json" {:schema specs/error-response-schema}}}}}
                          :name ::current-price-by-symbol}]

     ["/history/:symbol" {:get {:handler handlers/get-price-history
                                :summary "Histórico de preços"
                                :description "Retorna o histórico de preços de uma moeda"
                                :tags ["prices"]
                                :parameters [specs/symbol-path-param
                                             specs/days-query-param
                                             specs/limit-query-param]
                                :responses {200 {:description "Histórico de preços"
                                                 :content {"application/json" {:schema specs/success-response-schema}}}
                                            400 {:description "Parâmetros inválidos"
                                                 :content {"application/json" {:schema specs/error-response-schema}}}
                                            404 {:description "Moeda não encontrada"
                                                 :content {"application/json" {:schema specs/error-response-schema}}}
                                            500 {:description "Erro interno"
                                                 :content {"application/json" {:schema specs/error-response-schema}}}}}
                         :name ::price-history}]]

    ["/market"
     ["/overview" {:get {:handler handlers/get-market-overview
                         :summary "Visão geral do mercado"
                         :description "Retorna estatísticas gerais do mercado de criptomoedas"
                         :tags ["market"]
                         :responses {200 {:description "Visão geral do mercado"
                                          :content {"application/json" {:schema specs/success-response-schema}}}
                                     500 {:description "Erro interno"
                                          :content {"application/json" {:schema specs/error-response-schema}}}}}
                   :name ::market-overview}]

     ["/gainers" {:get {:handler handlers/get-top-gainers
                        :summary "Maiores altas"
                        :description "Retorna as moedas com maiores ganhos em 24h"
                        :tags ["market"]
                        :parameters [specs/limit-query-param]
                        :responses {200 {:description "Maiores altas"
                                         :content {"application/json" {:schema {:allOf [specs/success-response-schema
                                                                                         {:properties {:data {:type "array"
                                                                                                              :items specs/price-schema}}}]}}}}
                                    500 {:description "Erro interno"
                                         :content {"application/json" {:schema specs/error-response-schema}}}}}
                  :name ::top-gainers}]

     ["/losers" {:get {:handler handlers/get-top-losers
                       :summary "Maiores baixas"
                       :description "Retorna as moedas com maiores perdas em 24h"
                       :tags ["market"]
                       :parameters [specs/limit-query-param]
                       :responses {200 {:description "Maiores baixas"
                                        :content {"application/json" {:schema {:allOf [specs/success-response-schema
                                                                                        {:properties {:data {:type "array"
                                                                                                             :items specs/price-schema}}}]}}}}
                                   500 {:description "Erro interno"
                                        :content {"application/json" {:schema specs/error-response-schema}}}}}
                 :name ::top-losers}]]

    ["/stats/:symbol" {:get {:handler handlers/get-coin-statistics
                             :summary "Estatísticas da moeda"
                             :description "Retorna estatísticas detalhadas de uma moeda"
                             :tags ["coins"]
                             :parameters [specs/symbol-path-param]
                             :responses {200 {:description "Estatísticas da moeda"
                                              :content {"application/json" {:schema specs/success-response-schema}}}
                                         404 {:description "Moeda não encontrada"
                                              :content {"application/json" {:schema specs/error-response-schema}}}
                                         500 {:description "Erro interno"
                                              :content {"application/json" {:schema specs/error-response-schema}}}}}
                       :name ::coin-statistics}]

    ["/system"
     ["/collect" {:post {:handler handlers/force-collection
                         :summary "Força coleta de dados"
                         :description "Força uma coleta imediata de dados das APIs externas"
                         :tags ["system"]
                         :parameters [{:name "coins"
                                       :in "query"
                                       :required false
                                       :description "Lista de moedas para coletar (padrão: todas)"
                                       :schema {:type "array"
                                                :items {:type "string"}
                                                :example ["bitcoin", "ethereum"]}}]
                         :responses {200 {:description "Coleta iniciada"
                                          :content {"application/json" {:schema specs/success-response-schema}}}
                                     500 {:description "Erro interno"
                                          :content {"application/json" {:schema specs/error-response-schema}}}}}
                  :name ::force-collection}]

     ["/status" {:get {:handler handlers/get-system-status
                       :summary "Status do sistema"
                       :description "Retorna o status completo do sistema"
                       :tags ["system"]
                       :responses {200 {:description "Status do sistema"
                                        :content {"application/json" {:schema specs/success-response-schema}}}
                                   500 {:description "Erro interno"
                                        :content {"application/json" {:schema specs/error-response-schema}}}}}
                 :name ::system-status}]]]])

(defn wrap-system
  "Middleware para injetar sistema nas requisições."
  [handler system]
  (fn [request]
    (handler (assoc request :system system))))

(defn create-app
  "Criando aplicação com Ring"
  [system]
  (let [;; Criando router
        router (reitit-ring/router (create-routes))

        ;; Criando handler principal
        app (reitit-ring/ring-handler
             router
             (reitit-ring/routes
              ;; Recursos estáticos
              (reitit-ring/create-resource-handler {:path "/"})
              ;; Handlers Padrão
              (reitit-ring/create-default-handler
               {:not-found not-found-handler
                :method-not-allowed method-not-allowed-handler
                :not-acceptable (constantly
                                 {:status 406
                                  :headers {"Content-Type" "application/json"}
                                  :body {:error "not_acceptable"}})})))]

    (log/info "Aplicação criada com sucesso!!")

    (-> app
        (wrap-system system)
        wrap-exceptions      ;; handler de exceção personalizado
        wrap-json-response   ;; resposta JSON personalizada
        wrap-debug-logging
        wrap-params
        wrap-keyword-params
        (wrap-cors :access-control-allow-origin [#".*"]
                   :access-control-allow-methods [:get :post :put :delete :options]
                   :access-control-allow-headers ["Content-Type" "Authorization"]))))

(defn start-server!
  "Inicializando HTTP server"
  [config system]
  (let [port (config/get-port config)
        app (create-app system)
        server (http-kit/run-server app {:port port})]
    ;; Inicializar WebSocket server
    (websocket/start-websocket-server!)

    (log/info (str "🚀 HTTP server inicializado na porta: " port))
    (log/info (str "🔌 WebSocket disponível em: ws://localhost:" port "/ws"))
    (log/info (str "❤️ Health check disponível em: http://localhost:" port "/api/health"))
    (log/info (str "📚 Documentação API (Swagger) disponível em: http://localhost:" port "/api-docs/"))
    server))

(defn stop-server!
  "Parando HTTP server"
  [server]
  (when server
    ;; Parar WebSocket server
    (websocket/stop-websocket-server!)

    ;; Parar HTTP server
    (server)
    (log/info "🛑 HTTP server finalizado!")))

(comment
  (def config (config/load-config))
  (def system {:db (atom nil)})
  (def server (start-server! config system))
  (stop-server! server))