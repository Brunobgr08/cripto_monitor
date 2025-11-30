(ns cripto-monitor.frontend.components.sidebar
  "Componente da barra lateral de navegação"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

;; ===== ESTILOS ESPECÍFICOS =====
(defn sidebar-styles []
  "Estilos específicos da sidebar"
  [:style
   ".sidebar {
      transition: width 0.3s ease;
      overflow-y: auto;
      overflow-x: hidden;
    }

    .sidebar.collapsed {
      width: 60px;
    }

    .sidebar-toggle {
      position: absolute;
      top: 10px;
      right: -15px;
      width: 30px;
      height: 30px;
      border: 1px solid #e2e8f0;
      border-radius: 50%;
      cursor: pointer;
      font-size: 12px;
      z-index: 10;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
    }

    .nav-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }

    .nav-item {
      margin-bottom: 2px;
    }

    .nav-link {
      display: flex;
      align-items: center;
      padding: 12px 20px;
      text-decoration: none;
      font-weight: 500;
      transition: all 0.2s ease;
      border-radius: 0 25px 25px 0;
      margin-right: 10px;
    }

    .nav-link:hover {
      background: #f8fafc;
    }

    .nav-link.active {
      background: linear-gradient(135deg, #667eea, #764ba2);
      box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
    }

    .nav-icon {
      margin-right: 12px;
      font-size: 18px;
      min-width: 20px;
    }

    .collapsed .nav-label {
      display: none;
    }

    .sidebar-stats {
      padding: 20px;
      border-top: 1px solid #f1f5f9;
      margin-top: 20px;
    }

    .stats-title {
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 15px;
    }

    .stat-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;
      border-bottom: 1px solid #f8fafc;
    }

    .stat-label {
      font-size: 13px;
    }

    .stat-value {
      font-size: 14px;
      font-weight: 600;
    }

    .stat-value.default {
      color: #10b981;
    }

    .stat-value.positive {
      color: #10b981;
    }

    .stat-value.negative {
      color: #ef4444;
    }

    .quick-list {
      margin-top: 15px;
    }

    .list-title {
      font-size: 14px;
      font-weight: 600;
      color: #374151;
      margin-bottom: 8px;
    }

    .quick-coin {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 4px 0;
      font-size: 12px;
    }

    .coin-symbol {
      font-weight: 600;
    }

    .coin-change {
      font-weight: 600;
    }

    .coin-change.positive {
      color: #10b981;
    }

    .coin-change.negative {
      color: #ef4444;
    }

    .connection-info {
      padding: 20px;
      border-top: 1px solid #f1f5f9;
    }

    .info-title {
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 15px;
    }

    .connection-status {
      space-y: 8px;
    }

    .status-item {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
    }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
    }

    .status-dot.online {
      background: #10b981;
    }

    .status-dot.offline {
      background: #ef4444;
    }

    .status-text {
      font-size: 13px;
      color: #64748b;
    }

    .last-update-info {
      margin-top: 10px;
      padding-top: 10px;
      border-top: 1px solid #f8fafc;
    }

    .last-update-info small {
      display: block;
      font-size: 11px;
      color: #9ca3af;
      line-height: 1.4;
    }

    .sidebar-footer {
      padding: 20px;
      border-top: 1px solid #f1f5f9;
      margin-top: auto;
    }

    .app-version {
      text-align: center;
      margin-bottom: 10px;
    }

    .app-version small {
      color: #9ca3af;
      font-size: 11px;
    }

    .footer-links {
      display: flex;
      justify-content: center;
      gap: 15px;
    }

    .footer-links a {
      color: #9ca3af;
      text-decoration: none;
      font-size: 16px;
      transition: color 0.2s ease;
    }

    .footer-links a:hover {
      color: #667eea;
    }"])

;; ===== ITENS DE NAVEGAÇÃO =====
(def navigation-items
  [{:id :dashboard
    :label "Dashboard"
    :icon "📊"
    :route "/"}
   {:id :prices
    :label "Preços"
    :icon "💰"
    :route "/prices"}
   {:id :charts
    :label "Gráficos"
    :icon "📈"
    :route "/charts"}
   {:id :alerts
    :label "Alertas"
    :icon "🔔"
    :route "/alerts"}
   {:id :portfolio
    :label "Portfolio"
    :icon "💼"
    :route "/portfolio"}
   {:id :settings
    :label "Configurações"
    :icon "⚙️"
    :route "/settings"}])

;; ===== COMPONENTES =====
(defn nav-item [{:keys [id label icon route active?]}]
  "Item individual de navegação"
  [:li.nav-item
   [:a.nav-link
    {:class (when active? "active")
     :href route
     :on-click (fn [e]
                 (.preventDefault e)
                 (rf/dispatch [:navigate-to id]))}
    [:span.nav-icon icon]
    [:span.nav-label label]]])

(defn quick-stats []
  "Estatísticas rápidas na sidebar"
  (let [market-stats @(rf/subscribe [:market-stats])
        top-gainers @(rf/subscribe [:top-gainers])
        top-losers @(rf/subscribe [:top-losers])]
    [:div.sidebar-stats
     [:h3.stats-title "📈 Resumo"]

     [:div.stat-item
      [:span.stat-label "Total de Moedas"]
      [:span.stat-value {:class "default"} (:total-coins market-stats 0)]]

     [:div.stat-item
      [:span.stat-label "Variação Média"]
      [:span.stat-value
       {:class (if (pos? (:avg-change market-stats 0)) "positive" "negative")}
       (str (if (pos? (:avg-change market-stats 0)) "+" "")
            (.toFixed (:avg-change market-stats 0) 2) "%")]]

    ;;  (when (seq top-gainers)
    ;;    [:div.quick-list
    ;;     [:h4.list-title "🚀 Maiores Altas"]
    ;;     (for [coin (take 3 top-gainers)]
    ;;       ^{:key (:symbol coin)}
    ;;       [:div.quick-coin
    ;;        [:span.coin-symbol (:symbol coin)]
    ;;        [:span.coin-change.positive
    ;;         (str "+" (.toFixed (:change_24h_percent coin) 1) "%")]])])

    ;;  (when (seq top-losers)
    ;;    [:div.quick-list
    ;;     [:h4.list-title "📉 Maiores Baixas"]
    ;;     (for [coin (take 3 top-losers)]
    ;;       ^{:key (:symbol coin)}
    ;;       [:div.quick-coin
    ;;        [:span.coin-symbol (:symbol coin)]
    ;;        [:span.coin-change.negative
    ;;         (str (.toFixed (:change_24h_percent coin) 1) "%")]])])
            ]))

;; (defn quick-stats-2 []
;;   "Estatísticas rápidas na sidebar"
;;   (let [market-stats @(rf/subscribe [:market-stats])
;;         top-gainers @(rf/subscribe [:top-gainers])
;;         top-losers @(rf/subscribe [:top-losers])]
;;     [:div.sidebar-stats
;;      [:h3.stats-title "📈 Resumo"]

;;      [:div.stat-item
;;       [:span.stat-label "Total de Moedas"]
;;       [:span.stat-value (:total-coins market-stats 0)]]

;;      [:div.stat-item
;;       [:span.stat-label "Variação Média"]
;;       [:span.stat-value
;;        {:class (if (pos? (:avg-change market-stats 0)) "positive" "negative")}
;;        (str (if (pos? (:avg-change market-stats 0)) "+" "")
;;             (.toFixed (:avg-change market-stats 0) 2) "%")]]

;;      (when (seq top-gainers)
;;        [:div.quick-list
;;         [:h4.list-title "🚀 Maiores Altas"]
;;         (for [coin (take 3 top-gainers)]
;;           ^{:key (:symbol coin)}
;;           [:div.quick-coin
;;            [:span.coin-symbol (:symbol coin)]
;;            [:span.coin-change.positive
;;             (str "+" (.toFixed (:change_24h_percent coin) 1) "%")]])])

;;      (when (seq top-losers)
;;        [:div.quick-list
;;         [:h4.list-title "📉 Maiores Baixas"]
;;         (for [coin (take 3 top-losers)]
;;           ^{:key (:symbol coin)}
;;           [:div.quick-coin
;;            [:span.coin-symbol (:symbol coin)]
;;            [:span.coin-change.negative
;;             (str (.toFixed (:change_24h_percent coin) 1) "%")]])])]))

(defn connection-info []
  "Informações de conexão"
  (let [status @(rf/subscribe [:connection-status])
        websocket-connected? @(rf/subscribe [:websocket-connected?])
        last-update @(rf/subscribe [:formatted-last-update])]
    [:div.connection-info
     [:h3.info-title "🔌 Conexão"]

     [:div.connection-status
      [:div.status-item
       [:span.status-dot {:class (name status)}]
       [:span.status-text
        (case status
          :online "API Online"
          :offline "API Offline"
          :connecting "Conectando..."
          "Desconhecido")]]

      [:div.status-item
       [:span.status-dot {:class (if websocket-connected? "online" "offline")}]
       [:span.status-text
        (if websocket-connected? "WebSocket Ativo" "WebSocket Inativo")]]]

     (when last-update
       [:div.last-update-info
        [:small "Última atualização:"]
        [:small last-update]])]))

(defn sidebar-footer []
  "Rodapé da sidebar"
  [:div.sidebar-footer
   [:div.app-version
    [:small "Versão 1.0.0"]]
   [:div.footer-links
    [:a {:href "#" :title "Documentação"} "📚"]
    [:a {:href "#" :title "Suporte"} "💬"]
    [:a {:href "#" :title "GitHub"} "🐙"]]])

(defn sidebar []
  "Componente principal da sidebar"
  (let [current-route @(rf/subscribe [:current-route])
        collapsed? @(rf/subscribe [:sidebar-collapsed?])]
    [:aside.sidebar
     {:class (when collapsed? "collapsed")}

     ;; Toggle button
     [:button.sidebar-toggle
      {:on-click #(rf/dispatch [:toggle-sidebar])}
      (if collapsed? "→" "←")]

     ;; Navegação principal
     [:nav.sidebar-nav
      [:ul.nav-list
       (for [item navigation-items]
         ^{:key (:id item)}
         [nav-item (assoc item :active? (= current-route (:id item)))])]]

     ;; Estatísticas rápidas
     (when-not collapsed?
       [quick-stats])

     ;; Informações de conexão
     (when-not collapsed?
       [connection-info])

     ;; Rodapé
     (when-not collapsed?
       [sidebar-footer])]))