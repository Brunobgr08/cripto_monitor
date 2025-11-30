(ns cripto-monitor.frontend.websocket
  "Cliente WebSocket para comunicação em tempo real"
  (:require [re-frame.core :as rf]
            [cripto-monitor.frontend.config :as config]
            [clojure.string :as str]))

;; Declaração forward para evitar warning
(declare connect!)

;; ===== ESTADO DO WEBSOCKET =====
(defonce websocket-state (atom {:connection nil
                                :connected? false
                                :reconnect-attempts 0
                                :max-reconnect-attempts 5
                                :reconnect-delay 1000
                                :heartbeat-interval nil
                                :subscriptions #{}}))

;; ===== CONFIGURAÇÃO =====
;; Usar configuração centralizada
(def websocket-config config/websocket-config)

;; ===== UTILITÁRIOS =====
(defn log [level message & args]
  "Log estruturado para WebSocket"
  (let [timestamp (.toISOString (js/Date.))
        formatted-message (str "🔌 [" timestamp "] " message)]
    (case level
      :info (apply js/console.info formatted-message args)
      :warn (apply js/console.warn formatted-message args)
      :error (apply js/console.error formatted-message args)
      :debug (apply js/console.debug formatted-message args)
      (apply js/console.log formatted-message args))))

(defn send-message! [message]
  "Envia mensagem via WebSocket"
  (let [connection (:connection @websocket-state)]
    (if (and connection (= (.-readyState connection) js/WebSocket.OPEN))
      (do
        (log :debug "Enviando mensagem:" message)
        (.send connection (js/JSON.stringify (clj->js message))))
      (log :warn "WebSocket não está conectado. Mensagem não enviada:" message))))

;; ===== HANDLERS DE MENSAGENS =====
(defmulti handle-websocket-message
  "Processa mensagens recebidas do servidor"
  (fn [message]
    (let [msg-type (:type message)]
      (cond
        (keyword? msg-type) msg-type
        (string? msg-type) (keyword msg-type)
        :else :default))))

(defmethod handle-websocket-message :welcome [message]
  (log :info "Conectado ao servidor WebSocket" (:client-id message))
  (rf/dispatch [:websocket-connected (:connection @websocket-state)])
  (rf/dispatch [:set-connection-status :online]))

(defmethod handle-websocket-message :price-update [message]
  (log :debug "Atualização de preço recebida:" (:data message))
  (rf/dispatch [:websocket-price-update (:data message)]))

(defmethod handle-websocket-message :market-update [message]
  (log :debug "Atualização de mercado recebida:" (:data message))
  (rf/dispatch [:websocket-market-update (:data message)]))

(defmethod handle-websocket-message :subscription-confirmed [message]
  (log :info "Subscrição confirmada:" (:coin-symbol message))
  (swap! websocket-state update :subscriptions conj (:coin-symbol message))
  (rf/dispatch [:notify {:title "Subscrição"
                         :message (str "Subscrição ativada para " (:coin-symbol message))
                         :type :success}]))

(defmethod handle-websocket-message :subscription-cancelled [message]
  (log :info "Subscrição cancelada:" (:coin-symbol message))
  (swap! websocket-state update :subscriptions disj (:coin-symbol message))
  (rf/dispatch [:notify {:title "Subscrição"
                         :message (str "Subscrição removida para " (:coin-symbol message))
                         :type :info}]))

(defmethod handle-websocket-message :pong [message]
  (log :debug "Pong recebido - conexão ativa"))

(defmethod handle-websocket-message :error [message]
  (log :error "Erro do servidor:" (:message message))
  (rf/dispatch [:notify {:title "Erro WebSocket"
                         :message (:message message)
                         :type :error}]))

(defmethod handle-websocket-message :default [message]
  (log :warn "Tipo de mensagem desconhecido:" (:type message) message))

;; ===== HEARTBEAT =====
(defn start-heartbeat! []
  "Inicia heartbeat para manter conexão ativa"
  (let [interval (js/setInterval
                  #(send-message! {:type :ping :timestamp (js/Date.now)})
                  (:heartbeat-interval websocket-config))]
    (swap! websocket-state assoc :heartbeat-interval interval)
    (log :debug "Heartbeat iniciado")))

(defn stop-heartbeat! []
  "Para o heartbeat"
  (when-let [interval (:heartbeat-interval @websocket-state)]
    (js/clearInterval interval)
    (swap! websocket-state assoc :heartbeat-interval nil)
    (log :debug "Heartbeat parado")))

;; ===== RECONEXÃO =====
(defn calculate-reconnect-delay [attempt]
  "Calcula delay de reconexão com backoff exponencial"
  (let [base-delay (:reconnect-delay websocket-config)
        max-delay (:max-reconnect-delay websocket-config)
        delay (* base-delay (Math/pow 2 attempt))]
    (min delay max-delay)))

(defn should-reconnect? []
  "Verifica se deve tentar reconectar"
  (let [state @websocket-state]
    (< (:reconnect-attempts state) (:max-reconnect-attempts state))))

(defn reconnect! []
  "Tenta reconectar ao WebSocket"
  (when (should-reconnect?)
    (let [attempt (:reconnect-attempts @websocket-state)
          delay (calculate-reconnect-delay attempt)]

      (log :info (str "Tentativa de reconexão " (inc attempt) "/"
                      (:max-reconnect-attempts websocket-config)
                      " em " delay "ms"))

      (swap! websocket-state update :reconnect-attempts inc)
      (rf/dispatch [:set-connection-status :connecting])

      (js/setTimeout connect! delay))))

;; ===== CONEXÃO PRINCIPAL =====
(defn connect! []
  "Conecta ao servidor WebSocket"
  (try
    (log :info "Conectando ao WebSocket:" (:url websocket-config))
    (rf/dispatch [:set-connection-status :connecting])

    (let [ws (js/WebSocket. (:url websocket-config))]

      ;; Configurar handlers
      (set! (.-onopen ws)
            (fn [event]
              (log :info "WebSocket conectado com sucesso")
              (swap! websocket-state merge {:connection ws
                                            :connected? true
                                            :reconnect-attempts 0})
              (start-heartbeat!)
              (rf/dispatch [:websocket-connected ws])
              (rf/dispatch [:set-connection-status :online])))

      (set! (.-onmessage ws)
            (fn [event]
              (try
                (let [data (js/JSON.parse (.-data event))
                      message (js->clj data :keywordize-keys true)]
                  (log :debug "Mensagem recebida:" message)
                  (handle-websocket-message message))
                (catch js/Error e
                  (log :error "Erro ao processar mensagem:" e)))))

      (set! (.-onclose ws)
            (fn [event]
              (log :warn "WebSocket desconectado. Código:" (.-code event) "Razão:" (.-reason event))
              (stop-heartbeat!)
              (swap! websocket-state merge {:connection nil
                                            :connected? false})
              (rf/dispatch [:websocket-disconnected])
              (rf/dispatch [:set-connection-status :offline])

              ;; Tentar reconectar se não foi fechamento intencional
              (when (not= (.-code event) 1000) ; 1000 = fechamento normal
                (reconnect!))))

      (set! (.-onerror ws)
            (fn [event]
              (log :error "Erro no WebSocket:" event)
              (rf/dispatch [:set-connection-status :offline])))

      ;; Armazenar conexão
      (swap! websocket-state assoc :connection ws))

    (catch js/Error e
      (log :error "Erro ao conectar WebSocket:" e)
      (rf/dispatch [:set-connection-status :offline])
      (reconnect!))))

(defn disconnect! []
  "Desconecta do WebSocket"
  (log :info "Desconectando WebSocket...")
  (stop-heartbeat!)

  (when-let [ws (:connection @websocket-state)]
    (.close ws 1000 "Desconexão solicitada pelo cliente"))

  (swap! websocket-state merge {:connection nil
                                :connected? false
                                :reconnect-attempts 0
                                :subscriptions #{}})

  (rf/dispatch [:websocket-disconnected])
  (rf/dispatch [:set-connection-status :offline]))

;; ===== API PÚBLICA =====
(defn subscribe-to-coin! [coin-symbol]
  "Subscreve a atualizações de uma moeda"
  (log :info "Subscrevendo a" coin-symbol)
  (send-message! {:type :subscribe :coin-symbol coin-symbol}))

(defn unsubscribe-from-coin! [coin-symbol]
  "Remove subscrição de uma moeda"
  (log :info "Removendo subscrição de" coin-symbol)
  (send-message! {:type :unsubscribe :coin-symbol coin-symbol}))

(defn get-subscriptions! []
  "Solicita lista de subscrições ativas"
  (send-message! {:type :get-subscriptions}))

(defn is-connected? []
  "Verifica se está conectado"
  (:connected? @websocket-state))

(defn get-connection-state []
  "Retorna estado atual da conexão"
  @websocket-state)

;; ===== INICIALIZAÇÃO =====
(defn init-websocket! []
  "Inicializa cliente WebSocket"
  (log :info "Inicializando cliente WebSocket...")
  (connect!))

(defn cleanup-websocket! []
  "Limpa recursos do WebSocket"
  (log :info "Limpando recursos do WebSocket...")
  (disconnect!))
