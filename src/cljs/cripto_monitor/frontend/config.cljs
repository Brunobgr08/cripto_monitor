(ns cripto-monitor.frontend.config
  "Configurações do frontend com suporte a variáveis de ambiente")

;; ===== CONFIGURAÇÃO DE AMBIENTE =====
(defn get-env
  "Obtém variável de ambiente com valor padrão"
  [key default]
  (or (aget js/process "env" (name key)) default))

;; ===== CONFIGURAÇÕES DA API =====
(def api-config
  {:base-url (get-env :FRONTEND_API_BASE_URL "http://localhost:3000")
   :timeout 10000})

;; ===== CONFIGURAÇÕES DO WEBSOCKET =====
(def websocket-config
  {:url (get-env :FRONTEND_WEBSOCKET_URL "ws://localhost:3000/ws")
   :heartbeat-interval 30000  ; 30 segundos
   :reconnect-delay 1000      ; 1 segundo inicial
   :max-reconnect-delay 30000 ; 30 segundos máximo
   :max-reconnect-attempts 10})

;; ===== CONFIGURAÇÕES GERAIS =====
(def app-config
  {:name "Cripto Monitor"
   :version "1.0.0"
   :environment (get-env :ENV "development")})

;; ===== FUNÇÕES AUXILIARES =====
(defn development?
  "Verifica se está em ambiente de desenvolvimento"
  []
  (= "development" (:environment app-config)))

(defn production?
  "Verifica se está em ambiente de produção"
  []
  (= "production" (:environment app-config)))

(defn get-api-url
  "Constrói URL completa da API"
  [endpoint]
  (str (:base-url api-config) endpoint))

(defn get-websocket-url
  "Obtém URL do WebSocket"
  []
  (:url websocket-config))

;; ===== CONFIGURAÇÕES DE DESENVOLVIMENTO =====
(when (development?)
  (println "🔧 Frontend rodando em modo desenvolvimento")
  (println "📡 API Base URL:" (:base-url api-config))
  (println "🔌 WebSocket URL:" (:url websocket-config)))
