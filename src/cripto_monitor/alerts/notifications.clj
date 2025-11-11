(ns cripto-monitor.alerts.notifications
  "Notification system for alerts"
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [tick.core :as t]
            [clojure.string :as str])
  (:import [java.time Instant]))

;; Notification channels
(defprotocol NotificationChannel
  (send-notification [this alert result] "Send notification through this channel"))

;; Webhook notification
(defrecord WebhookNotification [url timeout-ms headers]
  NotificationChannel
  (send-notification [this alert result]
    (try
      (let [payload {:alert {:id (:id alert)
                             :coin-symbol (:coin-symbol alert)
                             :type (:type alert)
                             :user-id (:user-id alert)}
                     :trigger {:message (:message result)
                               :timestamp (str (t/instant))
                               :data (dissoc result :triggered :message)}}
            response (http/post url
                                {:body (json/generate-string payload)
                                 :headers (merge {"Content-Type" "application/json"} headers)
                                 :socket-timeout timeout-ms
                                 :connection-timeout timeout-ms
                                 :throw-exceptions false})]
        (if (< (:status response) 400)
          (do
            (log/info "Webhook notification sent successfully" 
                      {:alert-id (:id alert) :status (:status response)})
            {:success true :status (:status response)})
          (do
            (log/error "Webhook notification failed" 
                       {:alert-id (:id alert) :status (:status response) :body (:body response)})
            {:success false :error "HTTP error" :status (:status response)})))
      (catch Exception e
        (log/error e "Failed to send webhook notification" {:alert-id (:id alert)})
        {:success false :error (.getMessage e)}))))

;; Email notification (placeholder - would integrate with email service)
(defrecord EmailNotification [smtp-config]
  NotificationChannel
  (send-notification [this alert result]
    (log/info "Email notification (not implemented)" 
              {:alert-id (:id alert) :message (:message result)})
    {:success false :error "Email notifications not implemented"}))

;; SMS notification (placeholder - would integrate with SMS service)
(defrecord SMSNotification [sms-config]
  NotificationChannel
  (send-notification [this alert result]
    (log/info "SMS notification (not implemented)" 
              {:alert-id (:id alert) :message (:message result)})
    {:success false :error "SMS notifications not implemented"}))

;; Slack notification
(defrecord SlackNotification [webhook-url channel username]
  NotificationChannel
  (send-notification [this alert result]
    (try
      (let [color (case (:type alert)
                    (:price-above :volume-spike) "good"
                    (:price-below :price-drop) "danger"
                    :price-change-percent "warning"
                    "good")
            attachment {:color color
                        :title (format "🚨 %s Alert: %s" 
                                       (str/upper-case (name (:type alert)))
                                       (:coin-symbol alert))
                        :text (:message result)
                        :fields [{:title "Coin" :value (:coin-symbol alert) :short true}
                                 {:title "Alert Type" :value (name (:type alert)) :short true}
                                 {:title "User" :value (:user-id alert) :short true}
                                 {:title "Time" :value (str (t/instant)) :short true}]
                        :footer "Cripto Monitor"
                        :ts (quot (System/currentTimeMillis) 1000)}
            payload {:channel channel
                     :username username
                     :attachments [attachment]}
            response (http/post webhook-url
                                {:body (json/generate-string payload)
                                 :headers {"Content-Type" "application/json"}
                                 :socket-timeout 5000
                                 :connection-timeout 5000
                                 :throw-exceptions false})]
        (if (= 200 (:status response))
          (do
            (log/info "Slack notification sent successfully" {:alert-id (:id alert)})
            {:success true})
          (do
            (log/error "Slack notification failed" 
                       {:alert-id (:id alert) :status (:status response)})
            {:success false :error "Slack API error"})))
      (catch Exception e
        (log/error e "Failed to send Slack notification" {:alert-id (:id alert)})
        {:success false :error (.getMessage e)}))))

;; Discord notification
(defrecord DiscordNotification [webhook-url]
  NotificationChannel
  (send-notification [this alert result]
    (try
      (let [color (case (:type alert)
                    (:price-above :volume-spike) 0x00ff00  ; Green
                    (:price-below :price-drop) 0xff0000   ; Red
                    :price-change-percent 0xffff00       ; Yellow
                    0x0099ff)                            ; Blue
            embed {:title (format "🚨 %s Alert" (str/upper-case (name (:type alert))))
                   :description (:message result)
                   :color color
                   :fields [{:name "Coin" :value (:coin-symbol alert) :inline true}
                            {:name "Type" :value (name (:type alert)) :inline true}
                            {:name "User" :value (:user-id alert) :inline true}]
                   :timestamp (str (t/instant))
                   :footer {:text "Cripto Monitor"}}
            payload {:embeds [embed]}
            response (http/post webhook-url
                                {:body (json/generate-string payload)
                                 :headers {"Content-Type" "application/json"}
                                 :socket-timeout 5000
                                 :connection-timeout 5000
                                 :throw-exceptions false})]
        (if (< (:status response) 400)
          (do
            (log/info "Discord notification sent successfully" {:alert-id (:id alert)})
            {:success true})
          (do
            (log/error "Discord notification failed" 
                       {:alert-id (:id alert) :status (:status response)})
            {:success false :error "Discord API error"})))
      (catch Exception e
        (log/error e "Failed to send Discord notification" {:alert-id (:id alert)})
        {:success false :error (.getMessage e)}))))

;; Notification manager
(defonce notification-channels (atom {}))

(defn register-notification-channel!
  "Register a notification channel"
  [channel-id channel]
  (swap! notification-channels assoc channel-id channel)
  (log/info "Notification channel registered" {:channel-id channel-id :type (type channel)}))

(defn unregister-notification-channel!
  "Unregister a notification channel"
  [channel-id]
  (swap! notification-channels dissoc channel-id)
  (log/info "Notification channel unregistered" {:channel-id channel-id}))

(defn list-notification-channels
  "List all registered notification channels"
  []
  (keys @notification-channels))

(defn send-alert-notification
  "Send alert notification through all registered channels"
  [alert result]
  (let [channels @notification-channels]
    (if (seq channels)
      (do
        (log/info "Sending alert notification" 
                  {:alert-id (:id alert) :channels (count channels)})
        (doseq [[channel-id channel] channels]
          (try
            (let [result (send-notification channel alert result)]
              (log/debug "Notification sent" 
                         {:channel-id channel-id :success (:success result)}))
            (catch Exception e
              (log/error e "Failed to send notification" 
                         {:channel-id channel-id :alert-id (:id alert)})))))
      (log/warn "No notification channels registered" {:alert-id (:id alert)}))))

;; Configuration helpers
(defn setup-webhook-notifications!
  "Setup webhook notifications from config"
  [config]
  (when-let [webhook-config (get-in config [:alerts :notification :webhook])]
    (when (:url webhook-config)
      (let [channel (->WebhookNotification 
                     (:url webhook-config)
                     (get webhook-config :timeout-ms 5000)
                     (get webhook-config :headers {}))]
        (register-notification-channel! :webhook channel)
        (log/info "Webhook notifications configured")))))

(defn setup-slack-notifications!
  "Setup Slack notifications from config"
  [config]
  (when-let [slack-config (get-in config [:alerts :notification :slack])]
    (when (:webhook-url slack-config)
      (let [channel (->SlackNotification 
                     (:webhook-url slack-config)
                     (get slack-config :channel "#alerts")
                     (get slack-config :username "Cripto Monitor"))]
        (register-notification-channel! :slack channel)
        (log/info "Slack notifications configured")))))

(defn setup-discord-notifications!
  "Setup Discord notifications from config"
  [config]
  (when-let [discord-config (get-in config [:alerts :notification :discord])]
    (when (:webhook-url discord-config)
      (let [channel (->DiscordNotification (:webhook-url discord-config))]
        (register-notification-channel! :discord channel)
        (log/info "Discord notifications configured")))))

(defn setup-all-notifications!
  "Setup all notification channels from config"
  [config]
  (setup-webhook-notifications! config)
  (setup-slack-notifications! config)
  (setup-discord-notifications! config)
  (log/info "All notification channels configured" 
            {:channels (list-notification-channels)}))

;; Test notification
(defn send-test-notification
  "Send a test notification to verify configuration"
  [channel-id]
  (if-let [channel (get @notification-channels channel-id)]
    (let [test-alert {:id "test-alert"
                      :coin-symbol "BTC"
                      :type :price-above
                      :user-id "test-user"}
          test-result {:triggered true
                       :message "This is a test notification from Cripto Monitor"
                       :current-price 50000
                       :threshold 49000}]
      (send-notification channel test-alert test-result))
    {:success false :error "Channel not found"}))

(comment
  ;; Development helpers
  (require '[cripto-monitor.config :as config])
  
  (def config (config/load-config))
  
  ;; Setup notifications
  (setup-all-notifications! config)
  
  ;; List channels
  (list-notification-channels)
  
  ;; Send test notification
  (send-test-notification :webhook))
