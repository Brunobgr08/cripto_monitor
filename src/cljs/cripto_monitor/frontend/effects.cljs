(ns cripto-monitor.frontend.effects
  "Effects customizados para o Re-frame"
  (:require [re-frame.core :as rf]))

;; ===== EFFECT PARA NOTIFICAÇÕES =====
(rf/reg-fx
 :notify
 (fn [{:keys [title message type]}]
   (println (str "🔔 Notificação [" type "]: " title " - " message))
   ;; Aqui poderia integrar com uma biblioteca de notificações
   ;; Por enquanto, apenas log no console
   (when (and js/window.Notification
              (= js/Notification.permission "granted"))
     (js/Notification. title #js {:body message
                                   :icon "/images/favicon.ico"}))))

;; ===== EFFECT PARA ARMAZENAMENTO LOCAL =====
(rf/reg-fx
 :local-storage-set
 (fn [{:keys [key value]}]
   (when js/localStorage
     (.setItem js/localStorage (name key) (pr-str value)))))

(rf/reg-fx
 :local-storage-remove
 (fn [key]
   (when js/localStorage
     (.removeItem js/localStorage (name key)))))

;; ===== EFFECT PARA TÍTULO DA PÁGINA =====
(rf/reg-fx
 :set-page-title
 (fn [title]
   (set! js/document.title (str title " - Cripto Monitor"))))

;; ===== EFFECT PARA SCROLL =====
(rf/reg-fx
 :scroll-to-top
 (fn [_]
   (.scrollTo js/window 0 0)))

(rf/reg-fx
 :scroll-to-element
 (fn [element-id]
   (when-let [element (.getElementById js/document element-id)]
     (.scrollIntoView element #js {:behavior "smooth"}))))

;; ===== EFFECT PARA CLIPBOARD =====
(rf/reg-fx
 :copy-to-clipboard
 (fn [text]
   (when js/navigator.clipboard
     (-> (.writeText js/navigator.clipboard text)
         (.then #(println "📋 Texto copiado para clipboard:" text))
         (.catch #(println "❌ Erro ao copiar para clipboard:" %))))))

;; ===== EFFECT PARA ANALYTICS =====
(rf/reg-fx
 :track-event
 (fn [{:keys [category action label value]}]
   (println (str "📊 Analytics: " category "/" action
                 (when label (str " - " label))
                 (when value (str " (" value ")"))))
   ;; Aqui integraria com Google Analytics ou similar
   ))

;; ===== EFFECT PARA CONSOLE LOG ESTRUTURADO =====
(rf/reg-fx
 :log
 (fn [{:keys [level message data]}]
   (let [log-fn (case level
                  :debug js/console.debug
                  :info js/console.info
                  :warn js/console.warn
                  :error js/console.error
                  js/console.log)]
     (if data
       (log-fn message (clj->js data))
       (log-fn message)))))

;; ===== EFFECT PARA TIMEOUT =====
(rf/reg-fx
 :timeout
 (fn [{:keys [ms dispatch]}]
   (js/setTimeout #(rf/dispatch dispatch) ms)))

;; ===== EFFECT PARA INTERVAL =====
(rf/reg-fx
 :interval
 (fn [{:keys [ms dispatch id]}]
   (let [interval-id (js/setInterval #(rf/dispatch dispatch) ms)]
     ;; Armazenar ID do interval para poder cancelar depois
     (rf/dispatch [:store-interval-id id interval-id]))))

(rf/reg-fx
 :clear-interval
 (fn [interval-id]
   (when interval-id
     (js/clearInterval interval-id))))

;; ===== EFFECT PARA FOCUS =====
(rf/reg-fx
 :focus-element
 (fn [element-id]
   (js/setTimeout
    #(when-let [element (.getElementById js/document element-id)]
       (.focus element))
    100)))

;; ===== EFFECT PARA DOWNLOAD =====
(rf/reg-fx
 :download-data
 (fn [{:keys [data filename content-type]}]
   (let [blob (js/Blob. #js [data] #js {:type content-type})
         url (.createObjectURL js/URL blob)
         link (.createElement js/document "a")]
     (set! (.-href link) url)
     (set! (.-download link) filename)
     (.appendChild js/document.body link)
     (.click link)
     (.removeChild js/document.body link)
     (.revokeObjectURL js/URL url))))

;; ===== EFFECT PARA VIBRAÇÃO (mobile) =====
(rf/reg-fx
 :vibrate
 (fn [pattern]
   (when js/navigator.vibrate
     (js/navigator.vibrate pattern))))

;; ===== EFFECT PARA FULLSCREEN =====
(rf/reg-fx
 :toggle-fullscreen
 (fn [_]
   (if js/document.fullscreenElement
     (.exitFullscreen js/document)
     (.requestFullscreen js/document.documentElement))))

;; ===== EFFECT PARA TEMA =====
(rf/reg-fx
 :set-theme
 (fn [theme]
   (let [body js/document.body]
     (.remove (.-classList body) "theme-light" "theme-dark")
     (.add (.-classList body) (str "theme-" (name theme))))))

;; ===== EFFECT PARA FAVICON =====
(rf/reg-fx
 :set-favicon
 (fn [url]
   (when-let [link (.querySelector js/document "link[rel='icon']")]
     (set! (.-href link) url))))

;; ===== EFFECT PARA META TAGS =====
(rf/reg-fx
 :set-meta-tag
 (fn [{:keys [name content]}]
   (when-let [meta (.querySelector js/document (str "meta[name='" name "']"))]
     (set! (.-content meta) content))))

;; ===== EFFECTS PARA WEBSOCKET =====
(rf/reg-fx
 :websocket-connect
 (fn [_]
   ;; Será implementado quando o websocket for carregado
   (when js/window.CriptoMonitorWebSocket
     (.connect js/window.CriptoMonitorWebSocket))))

(rf/reg-fx
 :websocket-disconnect
 (fn [_]
   (when js/window.CriptoMonitorWebSocket
     (.disconnect js/window.CriptoMonitorWebSocket))))

(rf/reg-fx
 :websocket-subscribe
 (fn [coin-symbol]
   (when js/window.CriptoMonitorWebSocket
     (.subscribeTocoin js/window.CriptoMonitorWebSocket coin-symbol))))

(rf/reg-fx
 :websocket-unsubscribe
 (fn [coin-symbol]
   (when js/window.CriptoMonitorWebSocket
     (.unsubscribeFromCoin js/window.CriptoMonitorWebSocket coin-symbol))))
