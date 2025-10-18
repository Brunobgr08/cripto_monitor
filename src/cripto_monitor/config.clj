(ns cripto-monitor.config
  "Gerenciamento de configurações usando Aero"
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]))

(def ^:private config-file "config.edn")

(defn load-config
  "Carrega configurações do arquivo resources/config.edn."
  ([]
   (load-config {}))
  ([profile]
   (try
     (let [config (aero/read-config (io/resource config-file) profile)]
       (log/info "Configurações carregadas com sucesso.")
       config)
     (catch Exception e
       (log/error e "Falha ao carregar configurações.")
       (throw e)))))

(defn get-env
  "Obtém variável de ambiente com valor padrão opcional"
  ([key]
   (System/getenv (name key)))
  ([key default]
   (or (System/getenv (name key)) default)))

(defn development?
  "Verifica se está em ambiente de desenvolvimento"
  []
  (= "development" (get-env :ENV "development")))

(defn production?
  "Verifica se está em ambiente de produção"
  []
  (= "production" (get-env :ENV "development")))

(defn get-database-url
  "Obtém URL do banco de dados do ambiente ou configurações"
  [config]
  (or (get-env :DATABASE_URL)
      (get-in config [:database :url])))

(defn get-redis-url
  "Obtém URL do Redis do ambiente ou configurações"
  [config]
  (or (get-env :REDIS_URL)
      (get-in config [:redis :url])))

(defn get-port
  "Obtém porta do servidor do ambiente ou configurações"
  [config]
  (or (some-> (get-env :PORT) Integer/parseInt)
      (get-in config [:app :port])
      3000))

(comment
  ;; Funções auxiliares de desenvolvimento
  (load-config)
  (development?)
  (production?)
  (get-env :DATABASE_URL))
