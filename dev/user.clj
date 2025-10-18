(ns user
  "Development namespace for REPL-driven development"
  (:require [clojure.tools.namespace.repl :as repl]
            [clojure.pprint :refer [pprint]]
            [cripto-monitor.core :as core]
            [cripto-monitor.config :as config]
            [cripto-monitor.db.core :as db]
            [cripto-monitor.server :as server]
            [cripto-monitor.collector.core :as collector]
            [taoensso.timbre :as log]))

;; Set log level for development
(log/set-level! :debug)

(defn start
  "Start the development system"
  []
  (core/start-system!))

(defn stop
  "Stop the development system"
  []
  (core/stop-system!))

(defn restart
  "Restart the development system"
  []
  (stop)
  (repl/refresh :after 'user/start))

(defn reset
  "Reset the development system"
  []
  (stop)
  (repl/refresh))

(defn system
  "Get current system state"
  []
  @core/system)

(defn config
  "Get current configuration"
  []
  (config/load-config))

(defn db
  "Get database spec"
  []
  (:db-spec (system)))

(defn health
  "Check system health"
  []
  (when-let [db-spec (db)]
    {:database (db/health-check db-spec)
     :system (if (system) :running :stopped)}))

(comment
  ;; Development workflow
  (start)
  (stop)
  (restart)
  (reset)
  
  ;; System inspection
  (system)
  (config)
  (health)
  
  ;; Database operations
  (db/execute! (db) ["SELECT * FROM coins LIMIT 5"])
  (db/execute! (db) ["SELECT * FROM latest_prices"])
  
  ;; Manual testing
  (require '[clj-http.client :as http])
  (http/get "http://localhost:3000/api/health"))
