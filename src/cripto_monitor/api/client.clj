(ns cripto-monitor.api.client
  "Cliente HTTP para APIs externas de criptomoedas"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [tick.core :as t]
            [cripto-monitor.config :as config])
  (:import [java.time Instant]))

;; Configuração - carregando arquivo config.edn
(defonce ^:private app-config (atom nil))

(defn init-config!
  "Inicializando configurações do arquivo config.edn"
  [config]
  (reset! app-config config)
  (log/info "Configuração do cliente de API inicializada!"))

(defn- get-api-config
  "Obtém configuraçao da API"
  [api-name]
  (get-in @app-config [:apis api-name]))

(defn- get-coingecko-config []
  (or (get-api-config :coingecko)
      {:base-url "https://api.coingecko.com/api/v3"
       :rate-limit {:requests-per-minute 50}}))

(defn- get-binance-config []
  (or (get-api-config :binance)
      {:base-url "https://api.binance.com/api/v3"
       :rate-limit {:requests-per-minute 1200}}))

;; Status do limite de taxa
(def ^:private last-request-times (atom {}))

(defn- wait-for-rate-limit
  "Garante que não excedemos os limites de taxa para uma API específica"
  [api-name]
  (let [config (get-api-config api-name)
        rate-limit (get config :rate-limit {})
        requests-per-minute (get rate-limit :requests-per-minute 60)
        delay-ms (/ 60000 requests-per-minute) ; Converte para milissegundos entre requisições
        now (System/currentTimeMillis)
        last-time (get @last-request-times api-name 0)
        elapsed (- now last-time)]
    (when (< elapsed delay-ms)
      (let [sleep-time (- delay-ms elapsed)]
        (log/debug "Limitando taxa" {:api api-name :sleep-ms sleep-time})
        (Thread/sleep sleep-time)))
    (swap! last-request-times assoc api-name (System/currentTimeMillis))))

(defn- make-request
  "Faz uma requisição HTTP com tratamento de erros e retentativas"
  [url & {:keys [retries timeout api-name headers]
          :or {retries 3 timeout 30000 api-name :coingecko headers {}}}]
  (wait-for-rate-limit api-name)
  (loop [attempt 1]
    (let [result
          (try
            (log/debug "Fazendo requisição de API" {:url url :attempt attempt})
            (let [response (http/get url
                                     (merge {:socket-timeout timeout
                                             :connection-timeout timeout
                                             :accept :json
                                             :as :json
                                             :throw-exceptions false}
                                            (when (seq headers) {:headers headers})))]
              (cond
                (= 200 (:status response))
                (do
                  (log/debug "Requisição de API realizada com sucesso." {:url url :status (:status response)})
                  {:success true :data (:body response)})

                (= 429 (:status response)) ; Limite de taxa
                (do
                  (log/warn "Limite de taxa, aguardando..." {:url url :attempt attempt})
                  (Thread/sleep (* 2000 attempt)) ; Retorno exponencial
                  {:retry true})

                (>= (:status response) 500) ; Erro do servidor
                (do
                  (log/warn "Erro do servidor, tentando novamente..." {:url url :status (:status response) :attempt attempt})
                  (Thread/sleep (* 1000 attempt))
                  {:retry true})

                :else
                {:error (ex-info "Falha na requisição de API"
                                {:status (:status response)
                                 :body (:body response)
                                 :url url})}))
            (catch Exception e
              (log/error e "Falha na requisição de API" {:url url :attempt attempt})
              (Thread/sleep (* 1000 attempt))
              {:error e}))]

      (cond
        (:success result) (:data result)
        (:retry result) (if (< attempt retries)
                          (recur (inc attempt))
                          (throw (ex-info "Max retries exceeded" {:url url :attempts attempt})))
        (:error result) (if (< attempt retries)
                          (recur (inc attempt))
                          (throw (:error result)))
        :else (throw (ex-info "Unexpected result" {:result result}))))))

(defn fetch-coin-prices
  "Busca preços atuais de várias moedas na CoinGecko"
  [coin-ids & {:keys [vs-currency include-market-cap include-24hr-vol include-24hr-change]
               :or {vs-currency "usd"
                    include-market-cap true
                    include-24hr-vol true
                    include-24hr-change true}}]
  (try
    (let [config (get-coingecko-config)
          base-url (:base-url config)
          api-key (:api-key config)
          ids-str (str/join "," coin-ids)
          url (str base-url "/simple/price")
          params {:ids ids-str
                  :vs_currencies vs-currency
                  :include_market_cap include-market-cap
                  :include_24hr_vol include-24hr-vol
                  :include_24hr_change include-24hr-change}
          query-string (str/join "&" (map (fn [[k v]] (str (name k) "=" v)) params))
          full-url (str url "?" query-string)
          headers (if api-key {"x-cg-demo-api-key" api-key} {})]

      (log/info "Buscando preços das moedas" {:coins coin-ids :currency vs-currency})
      (let [response (make-request full-url :api-name :coingecko :headers headers)]
        (log/info "Preços buscados com sucesso" {:count (count response)})
        response))
    (catch Exception e
      (log/error e "Falha ao buscar preços das moedas" {:coins coin-ids})
      (throw e))))

(defn fetch-coin-history
  "Busca dados históricos de preços para uma moeda específica"
  [coin-id days & {:keys [vs-currency interval]
                   :or {vs-currency "usd" interval "daily"}}]
  (try
    (let [config (get-coingecko-config)
          base-url (:base-url config)
          api-key (:api-key config)
          url (str base-url "/coins/" coin-id "/market_chart")
          params {:vs_currency vs-currency
                  :days days
                  :interval interval}
          query-string (str/join "&" (map (fn [[k v]] (str (name k) "=" v)) params))
          full-url (str url "?" query-string)
          headers (if api-key {"x-cg-demo-api-key" api-key} {})]

      (log/info "Buscando histórico da moeda" {:coin coin-id :days days :currency vs-currency})
      (let [response (make-request full-url :api-name :coingecko :headers headers)]
        (log/info "Histórico da moeda buscado com sucesso" {:coin coin-id :data-points (count (:prices response))})
        response))
    (catch Exception e
      (log/error e "Falha ao buscar o hitórico da moeda" {:coin coin-id :days days})
      (throw e))))

(defn fetch-coin-details
  "Busca informações detalhadas sobre uma moeda específica"
  [coin-id]
  (try
    (let [config (get-coingecko-config)
          base-url (:base-url config)
          api-key (:api-key config)
          url (str base-url "/coins/" coin-id)
          params {:localization false
                  :tickers false
                  :market_data true
                  :community_data false
                  :developer_data false
                  :sparkline false}
          query-string (str/join "&" (map (fn [[k v]] (str (name k) "=" v)) params))
          full-url (str url "?" query-string)
          headers (if api-key {"x-cg-demo-api-key" api-key} {})]

      (log/info "Buscando detalhes da moeda" {:coin coin-id})
      (let [response (make-request full-url :api-name :coingecko :headers headers)]
        (log/info "Detalhes da moeda buscados com sucesso" {:coin coin-id})
        response))
    (catch Exception e
      (log/error e "Falha ao buscar detalhes da moeda" {:coin coin-id})
      (throw e))))

(defn list-supported-coins
  "Lista todas as moedas suportadas na CoinGecko"
  []
  (try
    (let [config (get-coingecko-config)
          base-url (:base-url config)
          api-key (:api-key config)
          url (str base-url "/coins/list")
          headers (if api-key {"x-cg-demo-api-key" api-key} {})]
      (log/info "Buscando lista de moedas suportadas")
      (let [response (make-request url :api-name :coingecko :headers headers)]
        (log/info "Lista de moedas suportadas buscada com sucesso" {:count (count response)})
        response))
    (catch Exception e
      (log/error e "Falha ao buscar lista de moedas suportadas")
      (throw e))))

(defn parse-price-data
  "Analisa dados de preços da resposta da CoinGecko para o formato da aplicação"
  [coin-id price-data]
  (let [now (t/instant)
        coin-key (if (keyword? coin-id) coin-id (keyword coin-id))
        coin-str (if (string? coin-id) coin-id (name coin-id))]
    {:coin_id coin-str
     :price_usd (get-in price-data [coin-key :usd])
     :market_cap (get-in price-data [coin-key :usd_market_cap])
     :volume_24h (get-in price-data [coin-key :usd_24h_vol])
     :change_24h_percent (get-in price-data [coin-key :usd_24h_change])
     :collected_at now
     :source "coingecko"}))

(defn parse-historical-data
  "Analisa dados históricos da resposta da CoinGecko para o formato da aplicação"
  [coin-id historical-data]
  (let [prices (:prices historical-data)
        market-caps (:market_caps historical-data)
        volumes (:total_volumes historical-data)
        coin-str (if (string? coin-id) coin-id (name coin-id))]
    (map (fn [[timestamp price] [_ market-cap] [_ volume]]
           {:coin_id coin-str
            :price_usd price
            :market_cap market-cap
            :volume_24h volume
            :change_24h_percent nil ; Não disponível nos dados históricos
            :collected_at (t/instant (Instant/ofEpochMilli timestamp))
            :source "coingecko"})
         prices market-caps volumes)))

;; Verificação de saúde da API
(defn api-health-check
  "Verifica se a API CoinGecko está acessível"
  []
  (try
    (let [config (get-coingecko-config)
          base-url (:base-url config)
          api-key (:api-key config)
          url (str base-url "/ping")
          headers (if api-key {"x-cg-demo-api-key" api-key} {})]
      (make-request url :timeout 5000 :api-name :coingecko :headers headers)
      {:status :healthy :api "coingecko"})
    (catch Exception e
      (log/error e "Falha na verificação de saúde da API")
      {:status :unhealthy :api "coingecko" :error (.getMessage e)})))

(comment
  ;; Testes de desenvolvimento
  (api-health-check)
  (fetch-coin-prices ["bitcoin" "ethereum"])
  (fetch-coin-history "bitcoin" 7)
  (fetch-coin-details "bitcoin")
  (list-supported-coins))
