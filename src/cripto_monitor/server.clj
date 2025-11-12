(ns cripto-monitor.server
  "HTTP server configurado com Ring e Reitit"
  (:require [org.httpkit.server :as http-kit]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [reitit.ring :as reitit-ring]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]
            [reitit.coercion.spec]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
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
  "Criando rotas com documentação Swagger organizadas por categoria"
  []
  [;; Swagger documentation routes
   ["/swagger.json" {:get {:no-doc true
                           :swagger specs/swagger-info
                           :handler (swagger/create-swagger-handler)}}]

   ["/api-docs/*" {:get {:no-doc true
                         :handler (swagger-ui/create-swagger-ui-handler
                                   {:url "/swagger.json"
                                    :config {:validatorUrl nil}})}}]

   ;; ============================================================================
   ;; HEALTH ENDPOINTS
   ;; ============================================================================
   ["/api/health" {:swagger {:tags ["health"]}
                   :get (merge {:handler handlers/health-check
                               :name ::health}
                               (get specs/route-docs ::health))}]

   ;; ============================================================================
   ;; COINS ENDPOINTS
   ;; ============================================================================
   ["/api/coins" {:swagger {:tags ["coins"]}
                  :get (merge {:handler handlers/get-all-coins
                               :name ::all-coins}
                               (get specs/route-docs ::all-coins))}]

   ["/api/coins/:symbol" {:swagger {:tags ["coins"]}
                          :get (merge {:handler handlers/get-coin-by-symbol
                                       :name ::coin-by-symbol}
                                       (get specs/route-docs ::coin-by-symbol))}]

   ;; ============================================================================
   ;; SEARCH ENDPOINTS
   ;; ============================================================================
   ["/api/search/coins" {:swagger {:tags ["search"]}
                         :get (merge {:handler handlers/search-coins
                                      :name ::search-coins}
                                      (get specs/route-docs ::search-coins))}]

   ;; ============================================================================
   ;; PRICES ENDPOINTS
   ;; ============================================================================
   ["/api/prices/current" {:swagger {:tags ["prices"]}
                           :get (merge {:handler handlers/get-current-prices
                                         :name ::current-prices}
                                         (get specs/route-docs ::current-prices))}]

   ["/api/prices/current/:symbol" {:swagger {:tags ["prices"]}
                                   :get (merge {:handler handlers/get-current-price-by-symbol
                                                 :name ::current-price-by-symbol}
                                                 (get specs/route-docs ::current-price-by-symbol))}]

   ["/api/prices/history/:symbol" {:swagger {:tags ["prices"]}
                                   :get (merge {:handler handlers/get-price-history
                                                 :name ::price-history}
                                                 (get specs/route-docs ::price-history))}]

   ;; ============================================================================
   ;; MARKET ENDPOINTS
   ;; ============================================================================
   ["/api/market/overview" {:swagger {:tags ["market"]}
                            :get (merge {:handler handlers/get-market-overview
                                         :name ::market-overview}
                                         (get specs/route-docs ::market-overview))}]

   ["/api/market/gainers" {:swagger {:tags ["market"]}
                           :get (merge {:handler handlers/get-top-gainers
                                         :name ::top-gainers}
                                         (get specs/route-docs ::top-gainers))}]

   ["/api/market/losers" {:swagger {:tags ["market"]}
                          :get (merge {:handler handlers/get-top-losers
                                        :name ::top-losers}
                                        (get specs/route-docs ::top-losers))}]

   ;; ============================================================================
   ;; ANALYTICS ENDPOINTS
   ;; ============================================================================
   ["/api/analytics/correlation" {:swagger {:tags ["analytics"]}
                                   :get (merge {:handler handlers/get-price-correlation
                                                 :name ::price-correlation}
                                                 (get specs/route-docs ::price-correlation))}]

   ["/api/analytics/portfolio" {:swagger {:tags ["analytics"]}
                                :post (merge {:handler handlers/get-portfolio-performance
                                               :name ::portfolio-performance}
                                               (get specs/route-docs ::portfolio-performance))}]

   ;; ============================================================================
   ;; STATISTICS ENDPOINTS
   ;; ============================================================================
   ["/api/stats/:symbol" {:swagger {:tags ["analytics"]}
                          :get (merge {:handler handlers/get-coin-statistics
                                       :name ::coin-statistics}
                                       (get specs/route-docs ::coin-statistics))}]

   ;; ============================================================================
   ;; SYSTEM ENDPOINTS
   ;; ============================================================================
   ["/api/system/collect" {:swagger {:tags ["system"]}
                           :post (merge {:handler handlers/force-collection
                                         :name ::force-collection}
                                         (get specs/route-docs ::force-collection))}]

   ["/api/system/status" {:swagger {:tags ["system"]}
                          :get (merge {:handler handlers/get-system-status
                                       :name ::system-status}
                                       (get specs/route-docs ::system-status))}]

   ;; ============================================================================
   ;; ALERTS ENDPOINTS
   ;; ============================================================================
   ["/api/alerts" {:swagger {:tags ["alerts"]}
                   :get (merge {:handler handlers/list-alerts
                                :name ::list-alerts}
                                (get specs/route-docs ::list-alerts))
                   :post (merge {:handler handlers/create-alert
                                 :name ::create-alert}
                                 (get specs/route-docs ::create-alert))}]

   ["/api/alerts/:alert-id" {:swagger {:tags ["alerts"]}
                             :get (merge {:handler handlers/get-alert
                                         :name ::get-alert}
                                         (get specs/route-docs ::get-alert))
                             :put (merge {:handler handlers/update-alert
                                         :name ::update-alert}
                                         (get specs/route-docs ::update-alert))
                             :delete (merge {:handler handlers/delete-alert
                                             :name ::delete-alert}
                                             (get specs/route-docs ::delete-alert))}]

   ;; ============================================================================
   ;; BINANCE ENDPOINTS
   ;; ============================================================================
   ["/api/binance/ticker" {:swagger {:tags ["binance"]}
                           :get (merge {:handler handlers/get-binance-ticker
                                        :name ::binance-ticker}
                                        (get specs/route-docs ::binance-ticker))}]

   ["/api/binance/klines/:symbol" {:swagger {:tags ["binance"]}
                                   :get (merge {:handler handlers/get-binance-klines
                                                 :name ::binance-klines}
                                                 (get specs/route-docs ::binance-klines))}]

   ["/api/binance/orderbook/:symbol" {:swagger {:tags ["binance"]}
                                      :get (merge {:handler handlers/get-binance-orderbook
                                                    :name ::binance-orderbook}
                                                    (get specs/route-docs ::binance-orderbook))}]])

(defn wrap-system
  "Middleware para injetar sistema nas requisições."
  [handler system]
  (fn [request]
    (handler (assoc request :system system))))

(defn create-app
  "Criando aplicação com Ring e Swagger"
  [system]
  (let [;; Criando router com middleware para Swagger
        router (reitit-ring/router
                (create-routes)
                {:data {:coercion reitit.coercion.spec/coercion
                        :muuntaja m/instance
                        :middleware [;; query-params & form-params
                                     parameters/parameters-middleware
                                     ;; content-negotiation
                                     muuntaja/format-negotiate-middleware
                                     ;; encoding response body
                                     muuntaja/format-response-middleware
                                     ;; decoding request body
                                     muuntaja/format-request-middleware
                                     ;; coercing response bodys
                                     coercion/coerce-response-middleware
                                     ;; coercing request parameters
                                     coercion/coerce-request-middleware]}})

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