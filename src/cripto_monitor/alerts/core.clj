(ns cripto-monitor.alerts.core
  "Alert system for cryptocurrency price monitoring"
  (:require [clojure.core.async :as async :refer [go go-loop <! >! timeout chan]]
            [taoensso.timbre :as log]
            [tick.core :as t]
            [tick.alpha.interval :as t.i]
            [cripto-monitor.db.core :as db]
            [cripto-monitor.alerts.notifications :as notifications])
  (:import [java.time Instant Duration]))

;; Alert system state
(defonce alert-system-state (atom {:running false
                                   :workers []
                                   :alerts {}
                                   :stats {:checks 0
                                           :triggered 0
                                           :errors 0}}))

;; Alert types
(def alert-types
  #{:price-above :price-below :price-change-percent :volume-spike :price-drop})

;; Alert conditions
(defprotocol AlertCondition
  (check-condition [this current-data historical-data] "Check if alert condition is met"))

(defrecord PriceAboveAlert [threshold]
  AlertCondition
  (check-condition [this current-data historical-data]
    (let [current-price (get current-data :price)]
      (when (and current-price (> current-price threshold))
        {:triggered true
         :message (format "Price above threshold: $%.2f (threshold: $%.2f)" current-price threshold)
         :current-price current-price
         :threshold threshold}))))

(defrecord PriceBelowAlert [threshold]
  AlertCondition
  (check-condition [this current-data historical-data]
    (let [current-price (get current-data :price)]
      (when (and current-price (< current-price threshold))
        {:triggered true
         :message (format "Price below threshold: $%.2f (threshold: $%.2f)" current-price threshold)
         :current-price current-price
         :threshold threshold}))))

;; (defrecord PriceChangePercentAlert [percent-threshold timeframe-minutes]
;;   AlertCondition
;;   (check-condition [this current-data historical-data]
;;     (let [current-price (get current-data :price)
;;           timeframe-ago (t/minus (t/instant) (t/new-duration timeframe-minutes :minutes))
;;           historical-price (some #(when (t/> (:timestamp %) timeframe-ago) (:price %)) historical-data)]
;;       (when (and current-price historical-price (> historical-price 0))
;;         (let [change-percent (* 100 (/ (- current-price historical-price) historical-price))]
;;           (when (>= (Math/abs change-percent) percent-threshold)
;;             {:triggered true
;;              :message (format "Price change: %.2f%% in %d minutes (threshold: %.2f%%)"
;;                               change-percent timeframe-minutes percent-threshold)
;;              :current-price current-price
;;              :historical-price historical-price
;;              :change-percent change-percent
;;              :timeframe-minutes timeframe-minutes}))))))

(defrecord PriceChangePercentAlert [percent-threshold timeframe-minutes]
  AlertCondition
  (check-condition [this current-data historical-data]
    (let [current-price (get current-data :price)
          timeframe-ago (t/- (t/instant) (Duration/ofMinutes timeframe-minutes))
          historical-price (some #(when (t/> (:timestamp %) timeframe-ago) (:price %)) historical-data)]
      (when (and current-price historical-price (> historical-price 0))
        (let [change-percent (* 100 (/ (- current-price historical-price) historical-price))]
          (when (>= (Math/abs change-percent) percent-threshold)
            {:triggered true
             :message (format "Price change: %.2f%% in %d minutes (threshold: %.2f%%)"
                              change-percent timeframe-minutes percent-threshold)
             :current-price current-price
             :historical-price historical-price
             :change-percent change-percent
             :timeframe-minutes timeframe-minutes}))))))

;; (defrecord VolumeSpikeAlert [volume-multiplier timeframe-minutes]
;;   AlertCondition
;;   (check-condition [this current-data historical-data]
;;     (let [current-volume (get current-data :volume)
;;           timeframe-ago (t/minus (t/instant) (t/new-duration timeframe-minutes :minutes))
;;           avg-volume (when (seq historical-data)
;;                        (let [historical-volumes (keep #(when (t/> (:timestamp %) timeframe-ago) (:volume %)) historical-data)]
;;                          (when (seq historical-volumes)
;;                            (/ (reduce + historical-volumes) (count historical-volumes)))))]
;;       (when (and current-volume avg-volume (> avg-volume 0))
;;         (let [volume-ratio (/ current-volume avg-volume)]
;;           (when (>= volume-ratio volume-multiplier)
;;             {:triggered true
;;              :message (format "Volume spike: %.2fx average volume (threshold: %.2fx)"
;;                               volume-ratio volume-multiplier)
;;              :current-volume current-volume
;;              :average-volume avg-volume
;;              :volume-ratio volume-ratio}))))))

(defrecord VolumeSpikeAlert [volume-multiplier timeframe-minutes]
  AlertCondition
  (check-condition [this current-data historical-data]
    (let [current-volume (get current-data :volume)
          timeframe-ago (t/- (t/instant) (Duration/ofMinutes timeframe-minutes))
          avg-volume (when (seq historical-data)
                       (let [historical-volumes (keep #(when (t/> (:timestamp %) timeframe-ago) (:volume %)) historical-data)]
                         (when (seq historical-volumes)
                           (/ (reduce + historical-volumes) (count historical-volumes)))))]
      (when (and current-volume avg-volume (> avg-volume 0))
        (let [volume-ratio (/ current-volume avg-volume)]
          (when (>= volume-ratio volume-multiplier)
            {:triggered true
             :message (format "Volume spike: %.2fx average volume (threshold: %.2fx)"
                              volume-ratio volume-multiplier)
             :current-volume current-volume
             :average-volume avg-volume
             :volume-ratio volume-ratio}))))))

;; Alert management
(defn create-alert
  "Create a new alert"
  [alert-id coin-symbol alert-type params user-id & {:keys [enabled] :or {enabled true}}]
  (let [condition (case alert-type
                    :price-above (->PriceAboveAlert (:threshold params))
                    :price-below (->PriceBelowAlert (:threshold params))
                    :price-change-percent (->PriceChangePercentAlert
                                           (:percent-threshold params)
                                           (:timeframe-minutes params))
                    :volume-spike (->VolumeSpikeAlert
                                   (:volume-multiplier params)
                                   (:timeframe-minutes params))
                    (throw (ex-info "Unknown alert type" {:type alert-type})))
        alert {:id alert-id
               :coin-symbol coin-symbol
               :type alert-type
               :condition condition
               :params params
               :user-id user-id
               :enabled enabled
               :created-at (str (t/instant))
               :last-checked nil
               :last-triggered nil
               :trigger-count 0}]
    (swap! alert-system-state assoc-in [:alerts alert-id] alert)
    (log/info "Alert created" {:alert-id alert-id :coin coin-symbol :type alert-type})
    alert))

(defn update-alert
  "Update an existing alert"
  [alert-id updates]
  (if (get-in @alert-system-state [:alerts alert-id])
    (do
      (swap! alert-system-state update-in [:alerts alert-id] merge updates)
      (log/info "Alert updated" {:alert-id alert-id :updates (keys updates)})
      (get-in @alert-system-state [:alerts alert-id]))
    (throw (ex-info "Alert not found" {:alert-id alert-id}))))

(defn delete-alert
  "Delete an alert"
  [alert-id]
  (if (get-in @alert-system-state [:alerts alert-id])
    (do
      (swap! alert-system-state update :alerts dissoc alert-id)
      (log/info "Alert deleted" {:alert-id alert-id})
      true)
    false))

(defn get-alert
  "Get alert by ID"
  [alert-id]
  (get-in @alert-system-state [:alerts alert-id]))

(defn list-alerts
  "List all alerts, optionally filtered by user or coin"
  [& {:keys [user-id coin-symbol enabled]}]
  (let [alerts (vals (get @alert-system-state :alerts))]
    (cond->> alerts
      user-id (filter #(= (:user-id %) user-id))
      coin-symbol (filter #(= (:coin-symbol %) coin-symbol))
      (some? enabled) (filter #(= (:enabled %) enabled)))))

;; Alert checking logic
(defn- check-alert
  "Check a single alert against current data"
  [alert db-spec]
  (try
    (let [{:keys [id coin-symbol condition]} alert
          current-data (first (db/get-latest-prices db-spec :coin-symbol coin-symbol :limit 1))
          historical-data (db/get-price-history db-spec coin-symbol :days 1)]

      (when current-data
        (let [result (check-condition condition current-data historical-data)]
          (swap! alert-system-state update-in [:alerts id] assoc :last-checked (str (t/instant)))
          (swap! alert-system-state update-in [:stats :checks] inc)

          (when (:triggered result)
            (swap! alert-system-state update-in [:alerts id]
                   #(-> %
                        (assoc :last-triggered (str (t/instant)))
                        (update :trigger-count inc)))
            (swap! alert-system-state update-in [:stats :triggered] inc)

            (log/info "Alert triggered" {:alert-id id :coin coin-symbol :result result})

            ;; Send notification
            (notifications/send-alert-notification alert result)

            result))))
    (catch Exception e
      (log/error e "Error checking alert" {:alert-id (:id alert)})
      (swap! alert-system-state update-in [:stats :errors] inc)
      nil)))

(defn- alert-checker-worker
  "Worker that continuously checks alerts"
  [db-spec check-interval-seconds]
  (go-loop []
    (try
      (let [enabled-alerts (list-alerts :enabled true)]
        (log/debug "Checking alerts" {:count (count enabled-alerts)})

        (doseq [alert enabled-alerts]
          (check-alert alert db-spec)))

      (catch Exception e
        (log/error e "Error in alert checker worker")
        (swap! alert-system-state update-in [:stats :errors] inc)))

    (<! (timeout (* check-interval-seconds 1000)))
    (when (:running @alert-system-state)
      (recur))))

;; System management
(defn start-alert-system!
  "Start the alert checking system"
  [config db-spec]
  (log/info "Starting alert system...")

  (when (:running @alert-system-state)
    (log/warn "Alert system already running")
    (throw (ex-info "Alert system already running" {})))

  (let [alert-config (:alerts config)
        check-interval (get alert-config :check-interval-seconds 60)
        checker-worker (alert-checker-worker db-spec check-interval)]

    (swap! alert-system-state assoc
           :running true
           :workers [checker-worker])

    (log/info "Alert system started" {:check-interval-seconds check-interval})
    @alert-system-state))

(defn stop-alert-system!
  "Stop the alert checking system"
  []
  (when (:running @alert-system-state)
    (log/info "Stopping alert system...")

    (swap! alert-system-state assoc :running false)

    ;; Workers will stop automatically when :running becomes false
    (Thread/sleep 1000) ; Give workers time to stop

    (swap! alert-system-state assoc :workers [])

    (log/info "Alert system stopped")))

(defn get-alert-system-status
  "Get current status of the alert system"
  []
  (let [state @alert-system-state]
    {:running (:running state)
     :alert-count (count (:alerts state))
     :enabled-alerts (count (list-alerts :enabled true))
     :stats (:stats state)}))

(comment
  ;; Development helpers
  (require '[cripto-monitor.config :as config])
  (require '[cripto-monitor.db.core :as db])

  (def config (config/load-config))
  (def db-spec (db/create-db-spec (:database config)))

  ;; Start alert system
  (start-alert-system! config db-spec)

  ;; Create test alerts
  (create-alert "btc-high" "BTC" :price-above {:threshold 50000} "user1")
  (create-alert "eth-change" "ETH" :price-change-percent {:percent-threshold 5 :timeframe-minutes 60} "user1")

  ;; Check status
  (get-alert-system-status)
  (list-alerts)

  ;; Stop system
  (stop-alert-system!))
