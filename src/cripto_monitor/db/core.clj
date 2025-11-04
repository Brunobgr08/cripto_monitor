(ns cripto-monitor.db.core
  "Conexão com o banco de dados e operações básicas"
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [next.jdbc.result-set :as rs]
            [hikari-cp.core :as hikari]
            [taoensso.timbre :as log]
            [cripto-monitor.config :as config])
  (:import [java.sql SQLException Timestamp]
           [java.time Instant]))

(defn create-db-spec
  "Criando especificação de banco de dados com pool de conexões"
  [db-config]
  (let [db-url (config/get-database-url {:database db-config})
        pool-config (merge {:jdbc-url db-url
                            :minimum-idle 2
                            :maximum-pool-size 10
                            :connection-timeout 30000
                            :idle-timeout 600000
                            :max-lifetime 1800000}
                           (:pool db-config))]
    (log/info "Criando pool de conexões com o banco de dados")
    {:datasource (hikari/make-datasource pool-config)}))

(defn close-db-spec!
  "Fechando pool de conexões com o banco de dados"
  [db-spec]
  (when-let [datasource (:datasource db-spec)]
    (hikari/close-datasource datasource)
    (log/info "Pool de conexões com o banco de dados fechado.")))

(defn health-check
  "Checando conexão com o banco de dados"
  [db-spec]
  (try
    (jdbc/execute! db-spec ["SELECT 1"])
    {:status :healthy}
    (catch SQLException e
      (log/error e "Falha ao verificar integridade do banco de dados.")
      {:status :unhealthy :error (.getMessage e)})))

;; Funções auxiliares de execução de consultas
(defn execute!
  "Executando SQL statement e retornando resultado múltiplo"
  [db-spec sql-params]
  (try
    (jdbc/execute! db-spec sql-params {:builder-fn rs/as-unqualified-lower-maps})
    (catch SQLException e
      (log/error e "Falha ao executar SQL" {:sql sql-params})
      (throw e))))

(defn execute-one!
  "Executando SQL statement e retornando resultado único"
  [db-spec sql-params]
  (try
    (jdbc/execute-one! db-spec sql-params {:builder-fn rs/as-unqualified-lower-maps})
    (catch SQLException e
      (log/error e "Falha ao executar SQL" {:sql sql-params})
      (throw e))))

(defn with-transaction
  "Executando função dentro de uma transação do banco de dados"
  [db-spec f]
  (jdbc/with-transaction [tx db-spec]
    (f tx)))

(defn instant->timestamp
  "Convertendo java.time.Instant para java.sql.Timestamp"
  [instant]
  (when instant
    (if (instance? Instant instant)
      (Timestamp/from instant)
      instant)))

(defn prepare-price-data
  "Preparando dados de preço para inserção no banco de dados convertendo tipos"
  [price-data]
  (-> price-data
      (update :collected_at instant->timestamp)
      (update :processed_at instant->timestamp)))

(defn insert!
  "Inserindo registro em uma tabela"
  [db-spec table data]
  (let [columns (keys data)
        values (vals data)
        placeholders (repeat (count columns) "?")
        sql (format "INSERT INTO %s (%s) VALUES (%s)"
                    (name table)
                    (clojure.string/join ", " (map name columns))
                    (clojure.string/join ", " placeholders))]
    (execute-one! db-spec (into [sql] values))))

(defn select
  "Selecionando registros de uma tabela"
  [db-spec table & {:keys [where limit offset order-by]}]
  (let [base-sql (format "SELECT * FROM %s" (name table))
        where-clause (when where
                       (format " WHERE %s" where))
        order-clause (when order-by
                       (format " ORDER BY %s" order-by))
        limit-clause (when limit
                       (format " LIMIT %d" limit))
        offset-clause (when offset
                        (format " OFFSET %d" offset))
        sql (str base-sql where-clause order-clause limit-clause offset-clause)]
    (execute! db-spec [sql])))

(defn update!
  "Atualizando registros em uma tabela"
  [db-spec table data where-clause]
  (let [set-clauses (map #(format "%s = ?" (name %)) (keys data))
        values (vals data)
        sql (format "UPDATE %s SET %s WHERE %s"
                    (name table)
                    (clojure.string/join ", " set-clauses)
                    where-clause)]
    (execute! db-spec (into [sql] values))))

(defn delete!
  "Removendo registros de uma tabela"
  [db-spec table where-clause]
  (let [sql (format "DELETE FROM %s WHERE %s" (name table) where-clause)]
    (execute! db-spec [sql])))

;; Operações de moeda
(defn get-coin-by-symbol
  "Obtendo moeda por símbolo"
  [db-spec symbol]
  (execute-one! db-spec ["SELECT * FROM coins WHERE symbol = ?" symbol]))

(defn get-coin-by-coingecko-id
  "Obtendo moeda por ID do CoinGecko"
  [db-spec coingecko-id]
  (execute-one! db-spec ["SELECT * FROM coins WHERE coingecko_id = ?" coingecko-id]))

(defn insert-coin!
  "Inserindo nova moeda"
  [db-spec coin-data]
  (execute-one! db-spec
    ["INSERT INTO coins (symbol, name, coingecko_id) VALUES (?, ?, ?) RETURNING *"
     (:symbol coin-data) (:name coin-data) (:coingecko_id coin-data)]))

(defn get-all-coins
  "Obtendo todas as moedas"
  [db-spec]
  (execute! db-spec ["SELECT * FROM coins ORDER BY symbol"]))

(defn get-coins
  "Obtendo moedas com filtros opcionais"
  [db-spec & {:keys [symbol name coingecko-id]}]
  (cond
    symbol (execute! db-spec ["SELECT * FROM coins WHERE symbol = ?" symbol])
    name (execute! db-spec ["SELECT * FROM coins WHERE name = ?" name])
    coingecko-id (execute! db-spec ["SELECT * FROM coins WHERE coingecko_id = ?" coingecko-id])
    :else (get-all-coins db-spec)))

;; Operações de histórico de preços
(defn insert-price-history!
  "Inserindo registro de histórico de preços"
  [db-spec price-data]
  (let [prepared-data (prepare-price-data price-data)]
    (execute-one! db-spec
      ["INSERT INTO price_history (coin_id, price_usd, market_cap, volume_24h, change_24h_percent, collected_at, source)
        VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *"
       (:coin_id prepared-data)
       (:price_usd prepared-data)
       (:market_cap prepared-data)
       (:volume_24h prepared-data)
       (:change_24h_percent prepared-data)
       (:collected_at prepared-data)
       (:source prepared-data)])))

(defn get-latest-prices
  "Obtendo preços mais recentes de todas as moedas"
  [db-spec]
  (execute! db-spec ["SELECT * FROM latest_prices"]))

(defn get-price-history
  "Obtendo histórico de preços de uma moeda"
  [db-spec coin-id & {:keys [limit days from-date to-date]}]
  (cond
    ;; Por dias
    days
    (execute! db-spec
      [(format "SELECT ph.*, c.symbol, c.name
        FROM price_history ph
        JOIN coins c ON ph.coin_id = c.id
        WHERE ph.coin_id = ? AND ph.collected_at >= NOW() - INTERVAL '%d days'
        ORDER BY ph.collected_at DESC
        LIMIT ?" days)
       coin-id (or limit 1000)])

    ;; Por intervalo de datas
    (and from-date to-date)
    (execute! db-spec
      ["SELECT ph.*, c.symbol, c.name
        FROM price_history ph
        JOIN coins c ON ph.coin_id = c.id
        WHERE ph.coin_id = ? AND ph.collected_at BETWEEN ? AND ?
        ORDER BY ph.collected_at DESC
        LIMIT ?"
       coin-id (instant->timestamp from-date) (instant->timestamp to-date) (or limit 1000)])

    ;; Padrão: histórico recente
    :else
    (execute! db-spec
      ["SELECT ph.*, c.symbol, c.name
        FROM price_history ph
        JOIN coins c ON ph.coin_id = c.id
        WHERE ph.coin_id = ?
        ORDER BY ph.collected_at DESC
        LIMIT ?"
       coin-id (or limit 100)])))

;; Operações de alerta
(defn insert-alert!
  "Inserindo novo alerta"
  [db-spec alert-data]
  (execute-one! db-spec
    ["INSERT INTO alerts (coin_id, alert_type, threshold_value, condition, notification_method, webhook_url, email)
      VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING *"
     (:coin_id alert-data)
     (:alert_type alert-data)
     (:threshold_value alert-data)
     (:condition alert-data)
     (:notification_method alert-data)
     (:webhook_url alert-data)
     (:email alert-data)]))

(defn get-active-alerts
  "Obtendo todos os alertas ativos"
  [db-spec]
  (execute! db-spec
    ["SELECT a.*, c.symbol, c.name
      FROM alerts a
      JOIN coins c ON a.coin_id = c.id
      WHERE a.is_active = true"]))

(defn trigger-alert!
  "Registrando disparo de alerta"
  [db-spec alert-id triggered-price]
  (with-transaction db-spec
    (fn [tx]
      ;; Inserindo histórico de disparo
      (let [history-record (execute-one! tx
                             ["INSERT INTO alert_history (alert_id, triggered_price) VALUES (?, ?) RETURNING *"
                              alert-id triggered-price])]
        ;; Atualizando último timestamp de disparo
        (execute! tx
          ["UPDATE alerts SET last_triggered = NOW() WHERE id = ?"
           alert-id])
        ;; Retornar o registro de histórico
        history-record))))

;; Funções de consulta avançadas para Issue 5
(defn get-price-statistics
  "Obtendo estatísticas de preços de uma moeda em um período"
  [db-spec coin-id days]
  (try
    (let [query (str "SELECT
        c.symbol,
        c.name,
        COUNT(ph.id) as record_count,
        MIN(ph.price_usd) as min_price,
        MAX(ph.price_usd) as max_price,
        AVG(ph.price_usd) as avg_price,
        STDDEV(ph.price_usd) as volatility,
        MIN(ph.collected_at) as first_record,
        MAX(ph.collected_at) as last_record,
        (MAX(ph.price_usd) - MIN(ph.price_usd)) / MIN(ph.price_usd) * 100 as price_range_percent
      FROM coins c
      LEFT JOIN price_history ph ON c.id = ph.coin_id
      WHERE c.id = '" coin-id "' AND ph.collected_at >= NOW() - INTERVAL '" days " days'
      GROUP BY c.id, c.symbol, c.name")]
      (log/info "Executando query de estatísticas:" query)
      (execute-one! db-spec [query]))
    (catch Exception e
      (log/error e "Erro ao buscar estatísticas de preço" {:coin-id coin-id :days days})
      nil)))

(defn get-top-gainers
  "Obtendo as moedas com maiores ganhos nos últimos 24h"
  [db-spec & {:keys [limit] :or {limit 10}}]
  (execute! db-spec
    ["SELECT
        c.symbol,
        c.name,
        latest.price_usd as current_price,
        latest.change_24h_percent,
        latest.market_cap,
        latest.volume_24h
      FROM coins c
      JOIN (
        SELECT DISTINCT ON (coin_id)
          coin_id, price_usd, change_24h_percent, market_cap, volume_24h
        FROM price_history
        ORDER BY coin_id, collected_at DESC
      ) latest ON c.id = latest.coin_id
      WHERE latest.change_24h_percent IS NOT NULL
      ORDER BY latest.change_24h_percent DESC
      LIMIT ?"
     limit]))

(defn get-top-losers
  "Obtendo as moedas com maiores perdas nos últimos 24h"
  [db-spec & {:keys [limit] :or {limit 10}}]
  (execute! db-spec
    ["SELECT
        c.symbol,
        c.name,
        latest.price_usd as current_price,
        latest.change_24h_percent,
        latest.market_cap,
        latest.volume_24h
      FROM coins c
      JOIN (
        SELECT DISTINCT ON (coin_id)
          coin_id, price_usd, change_24h_percent, market_cap, volume_24h
        FROM price_history
        ORDER BY coin_id, collected_at DESC
      ) latest ON c.id = latest.coin_id
      WHERE latest.change_24h_percent IS NOT NULL
      ORDER BY latest.change_24h_percent ASC
      LIMIT ?"
     limit]))

(defn get-market-overview
  "Obtendo visão geral do mercado com capitalização total e volume"
  [db-spec]
  (execute-one! db-spec
    ["SELECT
        COUNT(DISTINCT c.id) as total_coins,
        SUM(latest.market_cap) as total_market_cap,
        SUM(latest.volume_24h) as total_volume_24h,
        AVG(latest.change_24h_percent) as avg_change_24h
      FROM coins c
      JOIN (
        SELECT DISTINCT ON (coin_id)
          coin_id, market_cap, volume_24h, change_24h_percent
        FROM price_history
        ORDER BY coin_id, collected_at DESC
      ) latest ON c.id = latest.coin_id"]))

(defn search-coins
  "Buscando moedas por símbolo ou nome"
  [db-spec query & {:keys [limit] :or {limit 20}}]
  (execute! db-spec
    ["SELECT c.*, latest.price_usd, latest.change_24h_percent
      FROM coins c
      LEFT JOIN (
        SELECT DISTINCT ON (coin_id)
          coin_id, price_usd, change_24h_percent
        FROM price_history
        ORDER BY coin_id, collected_at DESC
      ) latest ON c.id = latest.coin_id
      WHERE LOWER(c.symbol) LIKE LOWER(?) OR LOWER(c.name) LIKE LOWER(?)
      ORDER BY c.symbol
      LIMIT ?"
     (str "%" query "%") (str "%" query "%") limit]))

(comment
  ;; Auxiliares para Desenvolvimento
  (def config (config/load-config))
  (def db-spec (create-db-spec (:database config)))
  (health-check db-spec)
  (get-all-coins db-spec)
  (get-latest-prices db-spec)
  (get-top-gainers db-spec :limit 5)
  (get-market-overview db-spec)
  (search-coins db-spec "bit")
  (close-db-spec! db-spec))
