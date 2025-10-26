(ns cripto-monitor.api.handlers
  "HTTP API handlers for cryptocurrency data"
  (:require [taoensso.timbre :as log]
            [cripto-monitor.db.core :as db]
            [cripto-monitor.collector.core :as collector]
            [tick.core :as t]
            [clojure.string :as str]
            [cheshire.core :as json]))

;; Response helpers
(defn success-response
  "Create successful JSON response"
  ([data] (success-response data 200))
  ([data status]
   {:status status
    :body {:success true
           :data data
           :timestamp (str (t/instant))}}))

(defn error-response
  "Create error JSON response"
  ([message] (error-response message 400))
  ([message status]
   {:status status
    :body {:success false
           :error message
           :timestamp (str (t/instant))}}))

(defn not-found-response
  "Create 404 response"
  [resource]
  (error-response (str resource " not found") 404))

;; Health check endpoint
(defn health-check
  "Health check endpoint"
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
      (log/error e "Health check failed")
      (error-response "Health check failed" 503))))

;; Coins endpoints
(defn get-all-coins
  "Get all available coins"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          coins (db/get-all-coins db-spec)]
      (success-response coins))
    (catch Exception e
      (log/error e "Failed to get coins")
      (error-response "Failed to retrieve coins" 500))))

(defn get-coin-by-symbol
  "Get specific coin by symbol"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          symbol (get-in request [:path-params :symbol])
          coin (db/get-coin-by-symbol db-spec (str/upper-case symbol))]
      (if coin
        (success-response coin)
        (not-found-response (str "Coin " symbol))))
    (catch Exception e
      (log/error e "Failed to get coin" {:symbol (get-in request [:path-params :symbol])})
      (error-response "Failed to retrieve coin" 500))))

;; Current prices endpoints
(defn get-current-prices
  "Get current prices for all coins"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          prices (db/get-latest-prices db-spec)]
      (success-response prices))
    (catch Exception e
      (log/error e "Failed to get current prices")
      (error-response "Failed to retrieve current prices" 500))))

(defn get-current-price-by-symbol
  "Get current price for specific coin"
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
            (error-response (str "No price data available for " symbol) 404)))
        (not-found-response (str "Coin " symbol))))
    (catch Exception e
      (log/error e "Failed to get current price" {:symbol (get-in request [:path-params :symbol])})
      (error-response "Failed to retrieve current price" 500))))

;; Price history endpoints
(defn get-price-history
  "Get price history for a coin"
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
                        (throw (ex-info "Invalid days parameter" {:status 400})))))
          limit (when limit-str
                  (try (Integer/parseInt limit-str)
                       (catch Exception _
                         (throw (ex-info "Invalid limit parameter" {:status 400})))))

          coin (db/get-coin-by-symbol db-spec (str/upper-case symbol))]

      (if coin
        (let [history (db/get-price-history db-spec (:id coin)
                                            :days days
                                            :limit limit)]
          (success-response
            {:coin coin
             :history history
             :count (count history)}))
        (not-found-response (str "Coin " symbol))))
    (catch clojure.lang.ExceptionInfo e
      (if (= 400 (:status (ex-data e)))
        (error-response (.getMessage e) 400)
        (throw e)))
    (catch NumberFormatException e
      (error-response "Invalid number format in query parameters" 400))
    (catch Exception e
      (log/error e "Failed to get price history" {:symbol (get-in request [:path-params :symbol])})
      (error-response "Failed to retrieve price history" 500))))

;; Market data endpoints
(defn get-market-overview
  "Get market overview statistics"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          overview (db/get-market-overview db-spec)]
      (success-response overview))
    (catch Exception e
      (log/error e "Failed to get market overview")
      (error-response "Failed to retrieve market overview" 500))))

(defn get-top-gainers
  "Get top gaining coins"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          limit (if-let [l (get-in request [:query-params "limit"])]
                  (Integer/parseInt l)
                  10)
          gainers (db/get-top-gainers db-spec :limit limit)]
      (success-response gainers))
    (catch NumberFormatException e
      (error-response "Invalid limit parameter" 400))
    (catch Exception e
      (log/error e "Failed to get top gainers")
      (error-response "Failed to retrieve top gainers" 500))))

(defn get-top-losers
  "Get top losing coins"
  [request]
  (try
    (let [db-spec (get-in request [:system :db-spec])
          limit (if-let [l (get-in request [:query-params "limit"])]
                  (Integer/parseInt l)
                  10)
          losers (db/get-top-losers db-spec :limit limit)]
      (success-response losers))
    (catch NumberFormatException e
      (error-response "Invalid limit parameter" 400))
    (catch Exception e
      (log/error e "Failed to get top losers")
      (error-response "Failed to retrieve top losers" 500))))

(defn search-coins
  "Search coins by symbol or name"
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
        (error-response (str "Query parameter 'q' must be at least 2 characters. Received: '" query "'") 400)))
    (catch NumberFormatException e
      (error-response "Invalid limit parameter" 400))
    (catch Exception e
      (log/error e "Failed to search coins")
      (error-response "Failed to search coins" 500))))

;; Statistics endpoints
(defn get-coin-statistics
  "Get statistics for a specific coin"
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
        (not-found-response (str "Coin " symbol))))
    (catch NumberFormatException e
      (error-response "Invalid days parameter" 400))
    (catch Exception e
      (log/error e "Failed to get coin statistics" {:symbol (get-in request [:path-params :symbol])})
      (error-response "Failed to retrieve coin statistics" 500))))

;; System endpoints
(defn force-collection
  "Force immediate data collection"
  [request]
  (try
    (let [coin-ids (or (get-in request [:query-params "coins"])
                       ["bitcoin" "ethereum" "solana" "cardano" "polkadot"])]
      (collector/force-collection (if (string? coin-ids) [coin-ids] coin-ids))
      (success-response {:message "Collection triggered"
                         :coins coin-ids}))
    (catch Exception e
      (log/error e "Failed to force collection")
      (error-response "Failed to trigger collection" 500))))
