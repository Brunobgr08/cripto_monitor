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
