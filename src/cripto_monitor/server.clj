(ns cripto-monitor.server
  "HTTP server configurado com Ring e Reitit"
  (:require [org.httpkit.server :as http-kit]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [reitit.ring :as reitit-ring]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [cripto-monitor.config :as config]
            [cripto-monitor.api.handlers :as handlers]
            [cripto-monitor.websocket.server :as websocket]))

(defn health-handler
  "Health check endpoint"
  [request]
  (log/info "Health check inicializado!" {:method (:request-method request) :uri (:uri request)})
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body {:status "ok"
          :timestamp (str (java.time.Instant/now))
          :version "1.0.0"
          :service "cripto-monitor"}})

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
  "Criando rotas"
  []
  [["/api/health" {:get health-handler
                   :name ::health}]

   ["/api/coins" {:get handlers/get-all-coins
                  :name ::all-coins}]

   ["/api/coins/:symbol" {:get handlers/get-coin-by-symbol
                          :name ::coin-by-symbol}]

   ["/api/search/coins" {:get handlers/search-coins
                         :name ::search-coins}]

   ["/api/prices/current" {:get handlers/get-current-prices
                           :name ::current-prices}]

   ["/api/prices/current/:symbol" {:get handlers/get-current-price-by-symbol
                                   :name ::current-price-by-symbol}]

   ["/api/prices/history/:symbol" {:get handlers/get-price-history
                                   :name ::price-history}]

   ["/api/market/overview" {:get handlers/get-market-overview
                            :name ::market-overview}]

   ["/api/market/gainers" {:get handlers/get-top-gainers
                           :name ::top-gainers}]

   ["/api/market/losers" {:get handlers/get-top-losers
                          :name ::top-losers}]

   ["/api/stats/:symbol" {:get handlers/get-coin-statistics
                          :name ::coin-statistics}]

   ["/api/system/collect" {:post handlers/force-collection
                           :name ::force-collection}]

   ;; ========================================
   ;; ISSUE 7: ALERT MANAGEMENT ENDPOINTS
   ;; ========================================
   ["/api/alerts" {:get handlers/list-alerts
                   :post handlers/create-alert
                   :name ::alerts}]

   ["/api/alerts/:alert-id" {:get handlers/get-alert
                             :put handlers/update-alert
                             :delete handlers/delete-alert
                             :name ::alert-by-id}]

   ;; ========================================
   ;; ISSUE 8: ADVANCED API ENDPOINTS
   ;; ========================================

   ;; Integração com Binance
   ["/api/binance/ticker" {:get handlers/get-binance-ticker
                           :name ::binance-ticker}]

   ["/api/binance/klines/:symbol" {:get handlers/get-binance-klines
                                   :name ::binance-klines}]

   ["/api/binance/orderbook/:symbol" {:get handlers/get-binance-orderbook
                                      :name ::binance-orderbook}]

   ;; Análises avançadas
   ["/api/analytics/correlation" {:get handlers/get-price-correlation
                                  :name ::price-correlation}]

   ["/api/analytics/portfolio" {:post handlers/get-portfolio-performance
                                :name ::portfolio-performance}]

   ;; Status do sistema
   ["/api/system/status" {:get handlers/get-system-status
                          :name ::system-status}]

   ;; ========================================
   ;; ISSUE 10: WEBSOCKET ENDPOINT
   ;; ========================================
   ["/ws" {:get websocket/websocket-handler
           :name ::websocket}]])

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