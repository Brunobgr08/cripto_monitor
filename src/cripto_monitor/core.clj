(ns cripto-monitor.core
  "Ponto de entrada principal da aplicação Cripto Monitor."
  (:require [cripto-monitor.config :as config]
            [cripto-monitor.server :as server]
            [cripto-monitor.collector.core :as collector]
            [cripto-monitor.db.core :as db]
            [cripto-monitor.api.client :as api-client]
            [cripto-monitor.api.binance :as binance]
            [cripto-monitor.alerts.core :as alerts]
            [cripto-monitor.alerts.notifications :as notifications]
            [taoensso.timbre :as log]
            [clojure.core.async :as async])
  (:gen-class))

(defonce system (atom nil))

(defn start-system!
  "Inicialiaza todos os componentes do sistema"
  []
  (log/info "Inicialiando sistema do Cripto Monitor...")

  (let [config (config/load-config)
        _ (api-client/init-config! config) ; Inicializando a configuração do cliente de API
        _ (binance/init-config! config)    ; Inicializando a configuração do cliente Binance
        _ (notifications/setup-all-notifications! config) ; Configurando os canais de notificação
        db-spec (db/create-db-spec (:database config))
        system-map {:config config :db-spec db-spec}
        server (server/start-server! config system-map)
        collector-system (collector/start-collector! config db-spec)
        alert-system (alerts/start-alert-system! config db-spec)]

    (reset! system {:config config
                    :db-spec db-spec
                    :server server
                    :collector collector-system
                    :alerts alert-system})

    (log/info "O sistema do Cripto Monitor foi inicializado com sucesso!")
    @system))

(defn stop-system!
  "Finaliando todos os componentes do sistema."
  []
  (when-let [sys @system]
    (log/info "Finaliando o sistema do Cripto Monitor...")

    ;; Parando sistema de alertas
    (when (:alerts sys)
      (alerts/stop-alert-system!))

    ;; Parando coletor
    (when (:collector sys)
      (collector/stop-collector!))

    ;; Parando servidor HTTP
    (when-let [server (:server sys)]
      (server/stop-server! server))

    (reset! system nil)
    (log/info "Cripto Monitor finalizado!")))

(defn restart-system!
  "Reiniciando o sistema do Cripto Monitor."
  []
  (stop-system!)
  (start-system!))

(defn -main
  "Ponto de entrada principal."
  [& args]
  (try
    (start-system!)

    ;; Adicionando hook para finalização
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. ^Runnable stop-system!))

    ;; Mantendo a thread principal ativa
    (async/<!! (async/chan))

    (catch Exception e
      (log/error e "Falha ao inicializar o sistema!")
      (System/exit 1))))

(comment
  ;; Auxiliares para Desenvolvimento
  (start-system!)
  (stop-system!)
  (restart-system!)

  ;; Checando o estado do sistema
  @system)
