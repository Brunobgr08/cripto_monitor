(ns cripto-monitor.frontend.core
  "Ponto de entrada principal do frontend ClojureScript"
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdom]
            [re-frame.core :as rf]
            [cripto-monitor.frontend.events]
            [cripto-monitor.frontend.subs]
            [cripto-monitor.frontend.views.main :as main]
            [cripto-monitor.frontend.effects]
            [cripto-monitor.frontend.websocket :as websocket]
            [day8.re-frame.http-fx]))

;; ===== CONFIGURAÇÃO DE DESENVOLVIMENTO =====
(defn dev-setup []
  "Configurações específicas para desenvolvimento"
  (when ^boolean goog.DEBUG
    (enable-console-print!)
    (println "🚀 Modo de desenvolvimento ativado")
    (rf/clear-subscription-cache!)))

;; ===== ROOT E MONTAGEM =====
(defonce root (atom nil))

(defn mount-root []
  "Monta o componente raiz da aplicação no DOM usando React 18"
  (rf/clear-subscription-cache!)
  (let [app-element (.getElementById js/document "app")]
    ;; Inicializa o root apenas uma vez
    (when-not @root
      (reset! root (rdom/create-root app-element)))

    ;; Renderiza o componente
    (rdom/render @root [main/main-panel])))

(defn ^:export init! []
  "Função principal de inicialização - chamada pelo Shadow-cljs"
  (println "🎯 Inicializando Cripto Monitor Frontend...")

  ;; Configurar desenvolvimento
  (dev-setup)

  ;; Inicializar Re-frame
  (rf/dispatch-sync [:initialize-db])

  ;; Montar aplicação
  (mount-root)

  ;; Iniciar coleta de dados
  (rf/dispatch [:fetch-initial-data])

  ;; Inicializar WebSocket
  (websocket/init-websocket!)

  (println "✅ Frontend inicializado com sucesso!"))

;; ===== HOT RELOAD =====
(defn ^:dev/after-load start []
  "Função chamada após hot reload - recarrega a aplicação"
  (println "🔄 Hot reload detectado - recarregando aplicação...")
  (rf/clear-subscription-cache!)
  (mount-root))

;; ===== CONFIGURAÇÃO DE PRODUÇÃO =====
(defn ^:export init-prod! []
  "Inicialização para ambiente de produção"
  (rf/dispatch-sync [:initialize-db])
  (mount-root)
  (rf/dispatch [:fetch-initial-data]))