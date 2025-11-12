(ns cripto-monitor.api.binance
  "Cliente API Binance para dados adicionais sobre criptomoedas"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [tick.core :as t]
            [cripto-monitor.config :as config])
  (:import [java.time Instant]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [java.util Base64]))

;; Configuração
(defonce ^:private app-config (atom nil))

(defn init-config!
  "Inicializando configurações do cliente Binance"
  [config]
  (reset! app-config config)
  (log/info "Configurações do cliente de API do Binance API inicializadas!"))

(defn- get-binance-config []
  (or (get-in @app-config [:apis :binance])
      {:base-url "https://api.binance.com/api/v3"
       :rate-limit {:requests-per-minute 1200}}))

;; Limite de taxa
(def ^:private last-request-time (atom 0))

(defn- wait-for-rate-limit
  "Certificar-se de não exceder os limites de taxa da Binance"
  []
  (let [config (get-binance-config)
        rate-limit (get config :rate-limit {})
        requests-per-minute (get rate-limit :requests-per-minute 1200)
        delay-ms (/ 60000 requests-per-minute)
        now (System/currentTimeMillis)
        elapsed (- now @last-request-time)]
    (when (< elapsed delay-ms)
      (Thread/sleep (- delay-ms elapsed)))
    (reset! last-request-time (System/currentTimeMillis))))

;; Assinatura HMAC SHA256 para solicitações autenticadas
(defn- hmac-sha256
  "Gera assinatura HMAC SHA256"
  [secret message]
  (let [mac (Mac/getInstance "HmacSHA256")
        secret-key (SecretKeySpec. (.getBytes secret "UTF-8") "HmacSHA256")]
    (.init mac secret-key)
    (let [signature (.doFinal mac (.getBytes message "UTF-8"))]
      (str/lower-case
       (apply str (map #(format "%02x" %) signature))))))

(defn- make-signed-request
  "Make authenticated request to Binance API"
  [endpoint params & {:keys [method] :or {method :get}}]
  (let [config (get-binance-config)
        base-url (:base-url config)
        api-key (:api-key config)
        secret-key (:secret-key config)]
    (if (and api-key secret-key)
      (let [timestamp (System/currentTimeMillis)
            query-params (assoc params :timestamp timestamp)
            query-string (str/join "&" (map (fn [[k v]] (str (name k) "=" v)) query-params))
            signature (hmac-sha256 secret-key query-string)
            signed-url (str base-url endpoint "?" query-string "&signature=" signature)
            headers {"X-MBX-APIKEY" api-key}]
        (wait-for-rate-limit)
        (try
          (let [response (http/request {:method method
                                        :url signed-url
                                        :headers headers
                                        :accept :json
                                        :as :json
                                        :throw-exceptions false})]
            (if (= 200 (:status response))
              {:success true :data (:body response)}
              {:success false :error (:body response) :status (:status response)}))
          (catch Exception e
            (log/error e "Binance API request failed" {:endpoint endpoint})
            {:success false :error (.getMessage e)})))
      (do
        (log/warn "Binance API credentials not configured")
        {:success false :error "API credentials not configured"}))))

(defn- make-public-request
  "Make public (unauthenticated) request to Binance API"
  [endpoint & {:keys [params]}]
  (let [config (get-binance-config)
        base-url (:base-url config)
        query-string (when params
                       (str/join "&" (map (fn [[k v]] (str (name k) "=" v)) params)))
        url (if query-string
              (str base-url endpoint "?" query-string)
              (str base-url endpoint))]
    (wait-for-rate-limit)
    (try
      (let [response (http/get url {:accept :json
                                    :as :json
                                    :throw-exceptions false})]
        (if (= 200 (:status response))
          {:success true :data (:body response)}
          {:success false :error (:body response) :status (:status response)}))
      (catch Exception e
        (log/error e "Binance public API request failed" {:endpoint endpoint})
        {:success false :error (.getMessage e)}))))

;; Symbol mapping (CoinGecko ID to Binance symbol)
(def ^:private symbol-mapping
  {"bitcoin" "BTCUSDT"
   "ethereum" "ETHUSDT"
   "solana" "SOLUSDT"
   "cardano" "ADAUSDT"
   "polkadot" "DOTUSDT"
   "binancecoin" "BNBUSDT"
   "ripple" "XRPUSDT"
   "dogecoin" "DOGEUSDT"
   "avalanche-2" "AVAXUSDT"
   "chainlink" "LINKUSDT"})

(defn- coingecko-id-to-binance-symbol
  "Convert CoinGecko ID to Binance symbol"
  [coingecko-id]
  (get symbol-mapping coingecko-id))

;; Public API functions
(defn fetch-ticker-prices
  "Fetch 24hr ticker price change statistics"
  [symbols]
  (try
    (let [binance-symbols (keep coingecko-id-to-binance-symbol symbols)]
      (if (seq binance-symbols)
        (let [response (make-public-request "/ticker/24hr")]
          (if (:success response)
            (let [all-tickers (:data response)
                  filtered-tickers (filter #(contains? (set binance-symbols) (:symbol %)) all-tickers)]
              {:success true :data filtered-tickers})
            response))
        {:success false :error "No valid Binance symbols found"}))
    (catch Exception e
      (log/error e "Failed to fetch Binance ticker prices")
      {:success false :error (.getMessage e)})))

(defn fetch-klines
  "Fetch kline/candlestick data"
  [symbol interval & {:keys [limit start-time end-time]}]
  (try
    (let [binance-symbol (coingecko-id-to-binance-symbol symbol)]
      (if binance-symbol
        (let [params (merge {:symbol binance-symbol :interval interval}
                            (when limit {:limit limit})
                            (when start-time {:startTime start-time})
                            (when end-time {:endTime end-time}))
              response (make-public-request "/klines" :params params)]
          response)
        {:success false :error (str "Symbol not supported: " symbol)}))
    (catch Exception e
      (log/error e "Failed to fetch Binance klines" {:symbol symbol})
      {:success false :error (.getMessage e)})))

(defn fetch-order-book
  "Fetch order book depth"
  [symbol & {:keys [limit] :or {limit 100}}]
  (try
    (let [binance-symbol (coingecko-id-to-binance-symbol symbol)]
      (if binance-symbol
        (let [params {:symbol binance-symbol :limit limit}
              response (make-public-request "/depth" :params params)]
          response)
        {:success false :error (str "Symbol not supported: " symbol)}))
    (catch Exception e
      (log/error e "Failed to fetch Binance order book" {:symbol symbol})
      {:success false :error (.getMessage e)})))

(defn fetch-recent-trades
  "Fetch recent trades"
  [symbol & {:keys [limit] :or {limit 500}}]
  (try
    (let [binance-symbol (coingecko-id-to-binance-symbol symbol)]
      (if binance-symbol
        (let [params {:symbol binance-symbol :limit limit}
              response (make-public-request "/trades" :params params)]
          response)
        {:success false :error (str "Symbol not supported: " symbol)}))
    (catch Exception e
      (log/error e "Failed to fetch Binance recent trades" {:symbol symbol})
      {:success false :error (.getMessage e)})))

;; Health check
(defn api-health-check
  "Check if Binance API is accessible"
  []
  (try
    (let [response (make-public-request "/ping")]
      (if (:success response)
        {:status :healthy :api "binance"}
        {:status :unhealthy :api "binance" :error (:error response)}))
    (catch Exception e
      (log/error e "Binance API health check failed")
      {:status :unhealthy :api "binance" :error (.getMessage e)})))

;; Account endpoints (require authentication)
(defn fetch-account-info
  "Fetch account information (requires API key)"
  []
  (make-signed-request "/account" {}))

(defn fetch-open-orders
  "Fetch open orders (requires API key)"
  [& {:keys [symbol]}]
  (let [params (if symbol {:symbol (coingecko-id-to-binance-symbol symbol)} {})]
    (make-signed-request "/openOrders" params)))

(comment
  ;; Teste de desenvolvimento
  (init-config! (config/load-config))
  (api-health-check)
  (fetch-ticker-prices ["bitcoin" "ethereum"])
  (fetch-klines "bitcoin" "1h" :limit 24)
  (fetch-order-book "bitcoin")
  (fetch-recent-trades "bitcoin" :limit 10))
