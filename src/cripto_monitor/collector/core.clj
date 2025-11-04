(ns cripto-monitor.collector.core
  "Sistema de coleta de dados utilizando core.async"
  (:require [clojure.core.async :as async :refer [go go-loop <! >! <!! >!! chan close! timeout]]
            [taoensso.timbre :as log]
            [tick.core :as t]
            [cripto-monitor.config :as config]
            [cripto-monitor.api.client :as api]
            [cripto-monitor.db.core :as db]
            [cripto-monitor.websocket.server :as websocket]))

;; Definindo canais principais
(def ^:private price-channel (chan 1000))
(def ^:private processing-channel (chan 100))
(def ^:private storage-channel (chan 50))
(def ^:private error-channel (chan 100))

;; Estado do sistema de coleta
(def ^:private collector-state (atom {:running false
                                      :workers []
                                      :scheduler nil
                                      :stats {:collected 0
                                              :processed 0
                                              :stored 0
                                              :errors 0}}))

(defn- update-stats!
  "Atualizando estatísticas do sistema de coleta"
  [stat-key]
  (swap! collector-state update-in [:stats stat-key] inc))

(defn- log-stats
  "Log de estatísticas atuais"
  []
  (let [stats (:stats @collector-state)]
    (log/info "Status da coleta:" stats)))

(defn- fetch-and-queue-prices
  "Busca de preços da Api e enfileiramento para processamento"
  [coin-ids]
  (go
    (try
      (log/debug "Buscando preços das moedas" {:coins coin-ids})
      (let [price-data (api/fetch-coin-prices coin-ids)]
        (doseq [[coin-id data] price-data]
          (let [coin-str (if (keyword? coin-id) (name coin-id) coin-id)
                parsed-data (api/parse-price-data coin-str {coin-id data})]
            (>! price-channel parsed-data)
            (update-stats! :collected)))
        (log/debug "Preços enfileirados com sucesso!" {:count (count price-data)}))
      (catch Exception e
        (log/error e "Falha ao buscar preços." {:coins coin-ids})
        (>! error-channel {:type :fetch-error :error e :coins coin-ids})
        (update-stats! :errors)))))

(defn- start-price-processor
  "Iniciar 'worker' para processar os dados de preços"
  []
  (go-loop []
    (when-let [price-data (<! price-channel)]
      (try
        (log/debug "Processando dados de preço." {:coin (:coin_id price-data)})

        ;; Adicione lógicas de processamento aqui, como validações, enriquecimento, etc.
        (let [processed-data (assoc price-data :processed_at (t/instant))]
          (>! processing-channel processed-data)
          (update-stats! :processed))

        (catch Exception e
          (log/error e "Erro ao processar dados de preço." {:data price-data})
          (>! error-channel {:type :processing-error :error e :data price-data})
          (update-stats! :errors)))
      (recur))))

(defn- start-storage-worker
    "Iniciar 'worker' para armazenar os dados processados."
  [db-spec]
  (go-loop []
    (when-let [processed-data (<! processing-channel)]
      (try
        (log/debug "Armazenando dados de preço." {:coin (:coin_id processed-data)})

        ;; Obtendo ID da moeda do banco de dados
        (let [coin (db/get-coin-by-coingecko-id db-spec (:coin_id processed-data))]
          (if coin
            (do
              (db/insert-price-history! db-spec
                                        (assoc processed-data :coin_id (:id coin)))
              (update-stats! :stored)
              (log/debug "Dados de preço armazenados com sucesso." {:coin (:symbol coin)})

              ;; Notificar clientes WebSocket sobre atualização de preço
              (websocket/broadcast-price-update!
                {:symbol (:symbol coin)
                 :name (:name coin)
                 :price_usd (:price_usd processed-data)
                 :change_24h_percent (:change_24h_percent processed-data)
                 :market_cap (:market_cap processed-data)
                 :volume_24h (:volume_24h processed-data)
                 :last_updated (:processed_at processed-data)}))
            (do
              (log/warn "Moeda não encontrada no banco de dados." {:coin-id (:coin_id processed-data)})
              (>! error-channel {:type :coin-not-found :coin-id (:coin_id processed-data)}))))

        (catch Exception e
          (log/error e "Erro ao armanezar dados de preço." {:data processed-data})
          (>! error-channel {:type :storage-error :error e :data processed-data})
          (update-stats! :errors)))
      (recur))))

(defn- start-error-handler
  "Inicia worker para gerenciar erros."
  []
  (go-loop []
    (when-let [error-data (<! error-channel)]
      (log/error "Handling error" error-data)
      ;; TODO: Implementar lógica de tratamento de erros, alertas, etc
      (recur))))

(defn- start-collection-scheduler
  "Inicia agendamento para coleta de dados periódicos."
  [interval-seconds coin-ids]
  (go-loop []
    (let [timeout-chan (timeout (* interval-seconds 1000))]
      (<! timeout-chan)
      (when (:running @collector-state)
        (fetch-and-queue-prices coin-ids)
        (recur)))))

(defn start-collector!
  "Inicia o sistema de coleta de dados completo"
  [config db-spec]
  (log/info "Iniciando sistema coletor de dados...")

  (when (:running @collector-state)
    (log/warn "Coletor já em execução.")
    (throw (ex-info "Coletor já em execução." {})))

  (let [collection-config (:collection config)
        interval-seconds (get collection-config :interval-seconds 60)
        coin-ids (get collection-config :coin-ids ["bitcoin" "ethereum" "solana" "cardano" "polkadot"])]

    ;; Inicia workers
    (let [price-processor (start-price-processor)
          storage-worker (start-storage-worker db-spec)
          error-handler (start-error-handler)
          scheduler (start-collection-scheduler interval-seconds coin-ids)]

      ;; Atualiza estado dos sistema
      (swap! collector-state assoc
             :running true
             :workers [price-processor storage-worker error-handler]
             :scheduler scheduler)

      ;; Inciando estatísticas do estados do sistema
      (go-loop []
        (let [timeout-chan (timeout 60000)] ; Log de estadi a cada minuto
          (<! timeout-chan)
          (when (:running @collector-state)
            (log-stats)
            (recur))))

      (log/info "Sistema coletor de dados iniciado!"
                {:interval-seconds interval-seconds
                 :coin-ids coin-ids})

      ;; Retorna estado atual
      {:running true
       :config config
       :db-spec db-spec
       :channels {:price price-channel
                  :processing processing-channel
                  :storage storage-channel
                  :error error-channel}})))

(defn stop-collector!
  "Finaliza o sistema de coleta de dados"
  []
  (log/info "Finalizando o sistema coletor de dados...")

  (if-not (:running @collector-state)
    (log/warn "Coletor não está em execução.")
    (do
      ;; Atualizando estado para parar agendador
      (swap! collector-state assoc :running false)

      ;; Fechando canais para finalizar workers
      (close! price-channel)
      (close! processing-channel)
      (close! storage-channel)
      (close! error-channel)

      ;; Resetando estado do sistema
      (reset! collector-state {:running false
                               :workers []
                               :scheduler nil
                               :stats {:collected 0
                                       :processed 0
                                       :stored 0
                                       :errors 0}})

      (log/info "Sistema coletor de dados finalizado!"))))

(defn get-collector-status
  "Obtém o status atual do sistema e estatísticas"
  []
  (let [state @collector-state]
    {:running (:running state)
     :worker-count (count (:workers state))
     :stats (:stats state)}))

(defn force-collection
  "Froça coleta imediata de dados (para testes e disparo manual)"
  [coin-ids]
  (if (:running @collector-state)
    (do
      (log/info "Forçando coleta immediata de dados..." {:coins coin-ids})
      (fetch-and-queue-prices coin-ids))
    (log/warn "Não é possível forçar coleta - coletor não está em execução.")))

(comment
  ;; Auxiliar de desenvolvimento
  (require '[cripto-monitor.config :as config])
  (require '[cripto-monitor.db.core :as db])

  (def config (config/load-config))
  (def db-spec (db/create-db-spec (:database config)))

  ;; Inicializando coletor
  (start-collector! config db-spec)

  ;; Checando estado do coletor
  (get-collector-status)

  ;; Force collection
  (force-collection ["bitcoin" "ethereum"])

  ;; Finalizando coletor
  (stop-collector!))
