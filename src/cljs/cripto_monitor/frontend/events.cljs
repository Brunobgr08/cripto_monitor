(ns cripto-monitor.frontend.events
  "Eventos do Re-frame para gerenciamento de estado"
  (:require [re-frame.core :as rf]
            [ajax.core :as ajax]
            [cripto-monitor.frontend.config :as config]
            [day8.re-frame.http-fx]))

;; ===== ESTADO INICIAL =====
(def default-db
  {:loading? false
   :error nil
   :connection-status :connecting
   :coins []
   :current-prices {}
   :selected-coin nil
   :price-history {}
   :alerts []
   :market-overview {}
   :websocket nil
   :last-update nil
   :filters {:search ""
             :sort-by :market-cap
             :sort-order :desc}
   :ui {:view-mode :detailed
        :sidebar-collapsed? false
        :current-route :dashboard
        :theme :light}})

;; ===== EVENTOS BÁSICOS =====
(rf/reg-event-fx
 :initialize-db
 (fn [_ _]
   (println "📊 Inicializando banco de dados do frontend...")
   {:db default-db
    :set-theme :light}))

(rf/reg-event-db
 :set-loading
 (fn [db [_ loading?]]
   (assoc db :loading? loading?)))

(rf/reg-event-db
 :set-error
 (fn [db [_ error]]
   (-> db
       (assoc :error error)
       (assoc :loading? false))))

(rf/reg-event-db
 :clear-error
 (fn [db _]
   (assoc db :error nil)))

;; ===== EVENTOS DE CONEXÃO =====
(rf/reg-event-db
 :set-connection-status
 (fn [db [_ status]]
   (println "🔌 Status de conexão alterado para:" status)
   (assoc db :connection-status status)))

;; ===== EVENTOS DE DADOS =====
(rf/reg-event-fx
 :fetch-initial-data
 (fn [{:keys [db]} _]
   (println "🔄 Buscando dados iniciais...")
   {:db (assoc db :loading? true)
    :dispatch-n [[:fetch-coins]
                 [:fetch-current-prices]
                 [:fetch-market-overview]]}))

(rf/reg-event-fx
 :fetch-coins
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (config/get-api-url "/api/coins")
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:fetch-coins-success]
                 :on-failure [:fetch-coins-failure]}}))

(rf/reg-event-db
 :fetch-coins-success
 (fn [db [_ response]]
   (println "✅ Moedas carregadas com sucesso:" (count (:data response)))
   (-> db
       (assoc :coins (:data response))
       (assoc :loading? false)
       (assoc :last-update (js/Date.)))))

(rf/reg-event-fx
 :fetch-coins-failure
 (fn [{:keys [db]} [_ error]]
   (println "❌ Erro ao carregar moedas:" error)
   {:db (-> db
            (assoc :error "Erro ao carregar lista de moedas")
            (assoc :loading? false))
    :dispatch [:set-connection-status :offline]}))

(rf/reg-event-fx
 :fetch-current-prices
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (config/get-api-url "/api/prices/current")
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:fetch-current-prices-success]
                 :on-failure [:fetch-current-prices-failure]}}))

(rf/reg-event-db
 :fetch-current-prices-success
 (fn [db [_ response]]
   (let [prices-map (reduce (fn [acc price]
                              (assoc acc (:symbol price) price))
                            {}
                            (:data response))]
     (println "💰 Preços atualizados para" (count prices-map) "moedas")
     (-> db
         (assoc :current-prices prices-map)
         (assoc :last-update (js/Date.))
         (assoc :connection-status :online)))))

(rf/reg-event-fx
 :fetch-current-prices-failure
 (fn [{:keys [db]} [_ error]]
   (println "❌ Erro ao carregar preços:" error)
   {:db (assoc db :error "Erro ao carregar preços atuais")
    :dispatch [:set-connection-status :offline]}))

(rf/reg-event-fx
 :fetch-market-overview
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri (config/get-api-url "/api/market/overview")
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:fetch-market-overview-success]
                 :on-failure [:fetch-market-overview-failure]}}))

(rf/reg-event-db
 :fetch-market-overview-success
 (fn [db [_ response]]
   (println "📈 Visão geral do mercado carregada")
   (assoc db :market-overview (:data response))))

(rf/reg-event-fx
 :fetch-market-overview-failure
 (fn [{:keys [db]} [_ error]]
   (println "❌ Erro ao carregar visão geral do mercado:" error)
   {:db (assoc db :error "Erro ao carregar dados do mercado")}))

;; ===== EVENTOS DE HISTÓRICO =====
(rf/reg-event-fx
 :fetch-price-history
 (fn [{:keys [db]} [_ symbol days]]
   (println "📊 Buscando histórico de preços para" symbol)
   {:http-xhrio {:method :get
                 :uri (config/get-api-url (str "/api/prices/history/" symbol "?days=" days))
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [:fetch-price-history-success symbol]
                 :on-failure [:fetch-price-history-failure symbol]}}))

(rf/reg-event-db
 :fetch-price-history-success
 (fn [db [_ symbol response]]
   (println "📈 Histórico carregado para" symbol)
   (assoc-in db [:price-history symbol] (:data response))))

(rf/reg-event-fx
 :fetch-price-history-failure
 (fn [{:keys [db]} [_ symbol error]]
   (println "❌ Erro ao carregar histórico para" symbol ":" error)
   {:db (assoc db :error (str "Erro ao carregar histórico de " symbol))}))

;; ===== EVENTOS DE SELEÇÃO =====
(rf/reg-event-db
 :select-coin
 (fn [db [_ coin-symbol]]
   (println "🎯 Moeda selecionada:" coin-symbol)
   (assoc db :selected-coin coin-symbol)))

;; ===== EVENTOS DE FILTROS =====
(rf/reg-event-db
 :set-search-filter
 (fn [db [_ search-term]]
   (assoc-in db [:filters :search] search-term)))

(rf/reg-event-db
 :set-sort
 (fn [db [_ sort-by sort-order]]
   (-> db
       (assoc-in [:filters :sort-by] sort-by)
       (assoc-in [:filters :sort-order] sort-order))))

;; ===== EVENTOS DE ATUALIZAÇÃO AUTOMÁTICA =====
(rf/reg-event-fx
 :start-auto-refresh
 (fn [{:keys [db]} _]
   (println "🔄 Iniciando atualização automática...")
   {:dispatch-later [{:ms 30000 :dispatch [:auto-refresh]}]}))

(rf/reg-event-fx
 :auto-refresh
 (fn [{:keys [db]} _]
   (when (= (:connection-status db) :online)
     {:dispatch-n [[:fetch-current-prices]]
      :dispatch-later [{:ms 30000 :dispatch [:auto-refresh]}]})))

;; ===== EVENTOS DE WEBSOCKET =====
(rf/reg-event-db
 :websocket-connected
 (fn [db [_ websocket]]
   (println "🔌 WebSocket conectado")
   (-> db
       (assoc :websocket websocket)
       (assoc :connection-status :online))))

(rf/reg-event-db
 :websocket-disconnected
 (fn [db _]
   (println "🔌 WebSocket desconectado")
   (-> db
       (assoc :websocket nil)
       (assoc :connection-status :offline))))

(rf/reg-event-db
 :websocket-price-update
 (fn [db [_ price-data]]
   (println "💰 Atualização de preço via WebSocket:" (:symbol price-data))
   (-> db
       (assoc-in [:current-prices (:symbol price-data)] price-data)
       (assoc :last-update (js/Date.)))))

(rf/reg-event-db
 :websocket-market-update
 (fn [db [_ market-data]]
   (println "📊 Atualização de mercado via WebSocket")
   (assoc db :market-overview market-data)))

(rf/reg-event-fx
 :websocket-subscribe-coin
 (fn [_ [_ coin-symbol]]
   (println "🔔 Subscrevendo a" coin-symbol "via WebSocket")
   {:websocket-subscribe coin-symbol}))

(rf/reg-event-fx
 :websocket-unsubscribe-coin
 (fn [_ [_ coin-symbol]]
   (println "🔕 Removendo subscrição de" coin-symbol "via WebSocket")
   {:websocket-unsubscribe coin-symbol}))

;; ===== EVENTOS DE UI =====
(rf/reg-event-db
 :set-view-mode
 (fn [db [_ mode]]
   (println "👁️ Modo de visualização alterado para:" mode)
   (assoc-in db [:ui :view-mode] mode)))

(rf/reg-event-db
 :toggle-sidebar
 (fn [db _]
   (let [collapsed? (get-in db [:ui :sidebar-collapsed?])]
     (println "📱 Sidebar" (if collapsed? "expandida" "recolhida"))
     (assoc-in db [:ui :sidebar-collapsed?] (not collapsed?)))))

(rf/reg-event-db
 :navigate-to
 (fn [db [_ route]]
   (println "🧭 Navegando para:" route)
   (assoc-in db [:ui :current-route] route)))

(rf/reg-event-fx
 :toggle-fullscreen
 (fn [_ _]
   {:toggle-fullscreen nil}))

(rf/reg-event-fx
 :toggle-theme
 (fn [{:keys [db]} _]
   (let [current-theme (get-in db [:ui :theme] :light)
         new-theme (if (= current-theme :light) :dark :light)]
     {:db (assoc-in db [:ui :theme] new-theme)
      :set-theme new-theme})))

;; ===== EVENTOS DE PORTFOLIO (preparação) =====
(rf/reg-event-fx
 :add-to-portfolio
 (fn [{:keys [db]} [_ symbol]]
   (println "💼 Adicionando" symbol "ao portfolio")
   {:notify {:title "Portfolio"
             :message (str symbol " adicionado ao portfolio")
             :type :success}}))

;; ===== EVENTOS DE ALERTAS (preparação) =====
(rf/reg-event-fx
 :create-alert
 (fn [{:keys [db]} [_ symbol]]
   (println "🔔 Criando alerta para" symbol)
   {:notify {:title "Alerta"
             :message (str "Alerta criado para " symbol)
             :type :success}}))
