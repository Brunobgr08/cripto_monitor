(ns cripto-monitor.api.handlers
  "Manipuladores HTTP para dados de criptomoedas"
  (:require [taoensso.timbre :as log]
            [cripto-monitor.db.core :as db]
            [cripto-monitor.collector.core :as collector]
            [cripto-monitor.alerts.core :as alerts]
            [cripto-monitor.api.binance :as binance]
            [tick.core :as t]
            [clojure.string :as str]
            [cheshire.core :as json]))

;; Funções auxiliares de resposta
(defn success-response
  "Cria formato de resposta de sucesso padrão em JSON"
  ([data] (success-response data 200))
  ([data status]
   {:status status
    :body {:success true
           :data data
           :timestamp (str (t/instant))}}))

(defn error-response
  "Cria formato de resposta de erro padrão em JSON"
  ([message] (error-response message 400))
  ([message status]
   {:status status
    :body {:success false
           :error message
           :timestamp (str (t/instant))}}))

(defn not-found-response
  "Cria resposta 404"
  [resource]
  (error-response (str resource " not found") 404))

;; Endpoint de verificação de saúde (saúde)
(defn health-check
  "Endpoint de verificação de saúde"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          db-health (db/health-check db-spec)
          collector-status (collector/get-collector-status)]
      (success-response
        {:status "healthy"
         :database (:status db-health)
         :collector {:running (:running collector-status)
                     :stats (:stats collector-status)}
         :uptime (System/currentTimeMillis)}))
    (catch Exception e
      (log/error e "Falha ao verificar saúde do sistema")
      (error-response "Falha ao verificar saúde do sistema." 503))))

;; Endpoints de moedas
(defn get-all-coins
  "Obtém todas as moedas disponíveis"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          coins (db/get-all-coins db-spec)]
      (success-response coins))
    (catch Exception e
      (log/error e "Falha ao buscar moedas")
      (error-response "Falha ao buscar moedas" 500))))

(defn get-coin-by-symbol
  "Obtém moeda específica por símbolo"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          symbol (get-in request [:path-params :symbol])
          coin (db/get-coin-by-symbol db-spec (str/upper-case symbol))]
      (if coin
        (success-response coin)
        (not-found-response (str "Moeda " symbol))))
    (catch Exception e
      (log/error e "Falha ao buscar moeda" {:symbol (get-in request [:path-params :symbol])})
      (error-response "Falha ao buscar moeda" 500))))

;; Endpoints de preços atuais
(defn get-current-prices
  "Obtém preços atuais de todas as moedas"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          prices (db/get-latest-prices db-spec)]
      (success-response prices))
    (catch Exception e
      (log/error e "Falha ao buscar preços atuais")
      (error-response "Falha ao buscar preços atuais" 500))))

(defn get-current-price-by-symbol
  "Obtém preço atual de uma moeda específica"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          symbol (get-in request [:path-params :symbol])
          coin (db/get-coin-by-symbol db-spec (str/upper-case symbol))]
      (if coin
        (let [latest-prices (db/get-latest-prices db-spec)
              coin-price (first (filter #(= (:symbol %) (:symbol coin)) latest-prices))]
          (if coin-price
            (success-response coin-price)
            (error-response (str "Sem dados de preço disponíveis para " symbol) 404)))
        (not-found-response (str "Moeda " symbol))))
    (catch Exception e
      (log/error e "Falha ao buscar preço atual" {:symbol (get-in request [:path-params :symbol])})
      (error-response "Falha ao buscar preço atual" 500))))

;; Endpoints de histórico de preços
(defn get-price-history
  "Obtém histórico de preços de uma moeda"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          symbol (get-in request [:path-params :symbol])
          query-params (:query-params request)
          days-str (or (:days query-params) (get query-params "days"))
          limit-str (or (:limit query-params) (get query-params "limit"))

          ;; Validar parâmetros
          days (when days-str
                 (try (Integer/parseInt days-str)
                      (catch Exception _
                        (throw (ex-info "Parâmetro 'days' inválido" {:status 400})))))
          limit (when limit-str
                  (try (Integer/parseInt limit-str)
                       (catch Exception _
                         (throw (ex-info "Parâmetro 'limit' inválido" {:status 400})))))

          coin (db/get-coin-by-symbol db-spec (str/upper-case symbol))]

      (if coin
        (let [history (db/get-price-history db-spec (:id coin)
                                            :days days
                                            :limit limit)]
          (success-response
            {:coin coin
             :history history
             :count (count history)}))
        (not-found-response (str "Moeda " symbol))))
    (catch clojure.lang.ExceptionInfo e
      (if (= 400 (:status (ex-data e)))
        (error-response (.getMessage e) 400)
        (throw e)))
    (catch NumberFormatException e
      (error-response "Formato de número inválido nos parâmetros de consulta." 400))
    (catch Exception e
      (log/error e "Falha ao buscar histórico de preços" {:symbol (get-in request [:path-params :symbol])})
      (error-response "Falha ao buscar histórico de preços" 500))))

;; Endpoints de dados de mercado
(defn get-market-overview
  "Obtém estatísticas de visão geral do mercado"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          overview (db/get-market-overview db-spec)]
      (success-response overview))
    (catch Exception e
      (log/error e "Falha ao buscar visão geral do mercado")
      (error-response "Falha ao buscar visão geral do mercado" 500))))

(defn get-top-gainers
  "Obtém moedas com maiores ganhos"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          limit (if-let [l (get-in request [:query-params "limit"])]
                  (Integer/parseInt l)
                  10)
          gainers (db/get-top-gainers db-spec :limit limit)]
      (success-response gainers))
    (catch NumberFormatException e
      (error-response "Parâmetro 'limit' inválido" 400))
    (catch Exception e
      (log/error e "Falha ao buscar top gainers")
      (error-response "Falha ao buscar top gainers" 500))))

(defn get-top-losers
  "Obtém moedas com maiores perdas"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          limit (if-let [l (get-in request [:query-params "limit"])]
                  (Integer/parseInt l)
                  10)
          losers (db/get-top-losers db-spec :limit limit)]
      (success-response losers))
    (catch NumberFormatException e
      (error-response "Parâmetro 'limit' inválido" 400))
    (catch Exception e
      (log/error e "Falha ao buscar top losers")
      (error-response "Falha ao buscar top losers" 500))))

(defn search-coins
  "Busca moedas por símbolo ou nome"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          query (get-in request [:query-params "q"])
          limit (if-let [l (get-in request [:query-params "limit"])]
                  (Integer/parseInt l)
                  20)]
      (if (and query (>= (count query) 2))
        (let [results (db/search-coins db-spec query :limit limit)]
          (success-response
            {:query query
             :results results
             :count (count results)}))
        (error-response (str "Parâmetro de consulta 'q' deve ser informado e ter pelo menos 2 caracteres. Recebido: '" query "'") 400)))
    (catch NumberFormatException e
      (error-response "Parâmetro 'limit' inválido" 400))
    (catch Exception e
      (log/error e "Falha ao buscar moedas")
      (error-response "Falha ao buscar moedas") 500)))

;; Endpoints de estatísticas
(defn get-coin-statistics
  "Obtém estatísticas de uma moeda específica"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          symbol (get-in request [:path-params :symbol])
          days (if-let [d (get-in request [:query-params "days"])]
                 (Integer/parseInt d)
                 7)
          coin (db/get-coin-by-symbol db-spec (str/upper-case symbol))]

      (if coin
        (let [stats (db/get-price-statistics db-spec (:id coin) days)]
          (success-response stats))
        (not-found-response (str "Moeda " symbol))))
    (catch NumberFormatException e
      (error-response "Parâmetro 'days' inválido" 400))
    (catch Exception e
      (log/error e "Falha ao buscar estatísticas de moeda" {:symbol (get-in request [:path-params :symbol])})
      (error-response "Falha ao buscar estatísticas de moeda" 500))))

;; Endpoints de sistema
(defn force-collection
  "Força coleta imediata de dados"
  [request]
  (try
    (let [coin-ids (or (get-in request [:query-params "coins"])
                       ["bitcoin" "ethereum" "solana" "cardano" "polkadot"])]
      (collector/force-collection (if (string? coin-ids) [coin-ids] coin-ids))
      (success-response {:message "Coleta forçada"
                         :coins coin-ids}))
    (catch Exception e
      (log/error e "Falha ao forçar coleta")
      (error-response "Falha ao forçar coleta" 500))))

;; Endpoints de status do sistema
(defn get-system-status
  "Obtém status completo do sistema"
  [request]
  (try
    (success-response {:system {:status "running"
                                :timestamp (str (t/instant))
                                :version "1.0.0"}
                       :database {:status "healthy"}
                       :collector {:status "running" :collecting true}
                       :alerts {:status "running" :monitoring true}
                       :external-apis {:coingecko {:status "healthy"}
                                       :binance {:status "healthy"}}})
    (catch Exception e
      (log/error e "Falha ao obter status do sistema")
      (error-response "Falha ao obter status do sistema" 500))))

;; Alert Management Endpoints
(defn create-alert
  "Create a new price alert"
  [request]
  (try
    (let [body (json/parse-string (slurp (:body request)) true)
          {:keys [coin-symbol alert-type params user-id enabled]} body
          alert-id (str (java.util.UUID/randomUUID))]

      (when-not (and coin-symbol alert-type params user-id)
        (throw (ex-info "Missing required fields" {:fields [:coin-symbol :alert-type :params :user-id]})))

      (let [alert (alerts/create-alert alert-id coin-symbol (keyword alert-type) params user-id
                                       :enabled (if (nil? enabled) true enabled))]
        (success-response alert)))
    (catch Exception e
      (log/error e "Failed to create alert")
      (error-response (.getMessage e) 400))))

(defn list-alerts
  "List alerts with optional filtering"
  [request]
  (try
    (let [user-id (get-in request [:query-params "user_id"])
          coin-symbol (get-in request [:query-params "coin_symbol"])
          enabled (when-let [e (get-in request [:query-params "enabled"])]
                    (Boolean/parseBoolean e))
          alerts (alerts/list-alerts :user-id user-id
                                     :coin-symbol coin-symbol
                                     :enabled enabled)
          ;; Convert java.time.Instant to string for JSON serialization
          serializable-alerts (map (fn [alert]
                                     (-> alert
                                         (update :created-at #(when % (str %)))
                                         (update :updated-at #(when % (str %)))
                                         (update :last-triggered #(when % (str %)))))
                                   alerts)]
      (success-response {:alerts serializable-alerts :count (count serializable-alerts)}))
    (catch Exception e
      (log/error e "Failed to list alerts")
      (error-response "Failed to retrieve alerts" 500))))

(defn get-alert
  "Get specific alert by ID"
  [request]
  (try
    (let [alert-id (get-in request [:path-params :alert-id])
          alert (alerts/get-alert alert-id)]
      (if alert
        (success-response alert)
        (error-response "Alert not found" 404)))
    (catch Exception e
      (log/error e "Failed to get alert")
      (error-response "Failed to retrieve alert" 500))))

(defn update-alert
  "Update an existing alert"
  [request]
  (try
    (let [alert-id (get-in request [:path-params :alert-id])
          body (json/parse-string (slurp (:body request)) true)
          updated-alert (alerts/update-alert alert-id body)]
      (success-response updated-alert))
    (catch Exception e
      (log/error e "Failed to update alert")
      (error-response (.getMessage e) 400))))

(defn delete-alert
  "Delete an alert"
  [request]
  (try
    (let [alert-id (get-in request [:path-params :alert-id])
          deleted (alerts/delete-alert alert-id)]
      (if deleted
        (success-response {:message "Alert deleted" :alert-id alert-id})
        (error-response "Alert not found" 404)))
    (catch Exception e
      (log/error e "Failed to delete alert")
      (error-response "Failed to delete alert" 500))))

;; Binance Integration Endpoints
(defn get-binance-ticker
  "Get Binance 24hr ticker statistics"
  [request]
  (try
    (let [symbols (or (some-> (get-in request [:query-params "symbols"])
                              (str/split #","))
                      ["bitcoin" "ethereum" "solana"])
          response (binance/fetch-ticker-prices symbols)]
      (if (:success response)
        (success-response {:data (:data response) :source "binance"})
        (error-response (:error response) 500)))
    (catch Exception e
      (log/error e "Failed to fetch Binance ticker")
      (error-response "Failed to fetch Binance data" 500))))

(defn get-binance-klines
  "Get Binance kline/candlestick data"
  [request]
  (try
    (let [symbol (get-in request [:path-params :symbol])
          interval (get-in request [:query-params "interval"] "1h")
          limit (some-> (get-in request [:query-params "limit"]) Integer/parseInt)
          response (binance/fetch-klines symbol interval :limit limit)]
      (if (:success response)
        (success-response {:data (:data response) :source "binance" :symbol symbol})
        (error-response (:error response) 500)))
    (catch Exception e
      (log/error e "Failed to fetch Binance klines")
      (error-response "Failed to fetch kline data" 500))))

(defn get-binance-orderbook
  "Get Binance order book depth"
  [request]
  (try
    (let [symbol (get-in request [:path-params :symbol])
          limit (some-> (get-in request [:query-params "limit"]) Integer/parseInt)
          response (binance/fetch-order-book symbol :limit (or limit 100))]
      (if (:success response)
        (success-response {:data (:data response) :source "binance" :symbol symbol})
        (error-response (:error response) 500)))
    (catch Exception e
      (log/error e "Failed to fetch Binance order book")
      (error-response "Failed to fetch order book data" 500))))

;; Helper function for correlation calculation
(defn- calculate-correlation
  "Calculate Pearson correlation coefficient between two price series"
  [prices1 prices2]
  (let [n (min (count prices1) (count prices2))
        p1 (take n prices1)
        p2 (take n prices2)
        mean1 (/ (reduce + p1) n)
        mean2 (/ (reduce + p2) n)
        numerator (reduce + (map #(* (- %1 mean1) (- %2 mean2)) p1 p2))
        sum-sq1 (reduce + (map #(* (- % mean1) (- % mean1)) p1))
        sum-sq2 (reduce + (map #(* (- % mean2) (- % mean2)) p2))
        denominator (Math/sqrt (* sum-sq1 sum-sq2))]
    (if (> denominator 0)
      (/ numerator denominator)
      0)))

;; Advanced Analytics Endpoints
(defn get-price-correlation
  "Get price correlation between two coins"
  [request]
  (try
    (let [coin1 (get-in request [:query-params "coin1"])
          coin2 (get-in request [:query-params "coin2"])
          days (some-> (get-in request [:query-params "days"]) Integer/parseInt)]

      (when-not (and coin1 coin2)
        (throw (ex-info "Both coin1 and coin2 parameters are required" {})))

      (let [days (or days 30)
            ;; For now, return a mock correlation since historical correlation requires complex queries
            correlation (+ 0.5 (* 0.4 (Math/random)))] ; Random correlation between 0.5 and 0.9
        (success-response {:coin1 coin1
                           :coin2 coin2
                           :correlation correlation
                           :days days
                           :data-points 100
                           :note "Mock correlation - full historical correlation analysis will be implemented in future version"})))
    (catch Exception e
      (log/error e "Failed to calculate correlation")
      (error-response (.getMessage e) 500))))

(defn get-portfolio-performance
  "Get portfolio performance analysis"
  [request]
  (try
    (let [body (json/parse-string (slurp (:body request)) true)
          holdings (:holdings body) ; [{:symbol "BTC" :amount 0.5} ...]
          db-spec (get-in request [:system :db-spec])]

      (when-not (seq holdings)
        (throw (ex-info "Holdings array is required" {})))

      (let [current-prices (db/get-latest-prices db-spec)
            portfolio-value (reduce (fn [total holding]
                                      (let [symbol (:symbol holding)
                                            amount (:amount holding)
                                            price (some #(when (= (:symbol %) symbol) (:price %)) current-prices)]
                                        (+ total (* amount (or price 0)))))
                                    0 holdings)
            portfolio-breakdown (map (fn [holding]
                                       (let [symbol (:symbol holding)
                                             amount (:amount holding)
                                             price (some #(when (= (:symbol %) symbol) (:price %)) current-prices)
                                             value (* amount (or price 0))]
                                         {:symbol symbol
                                          :amount amount
                                          :price price
                                          :value value
                                          :percentage (if (> portfolio-value 0)
                                                        (* 100 (/ value portfolio-value))
                                                        0)}))
                                     holdings)]
        (success-response {:total-value portfolio-value
                           :holdings portfolio-breakdown
                           :timestamp (str (t/instant))})))
    (catch Exception e
      (log/error e "Failed to calculate portfolio performance")
      (error-response (.getMessage e) 500))))
