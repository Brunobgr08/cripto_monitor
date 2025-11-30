(ns cripto-monitor.frontend.views.main
  "View principal da aplicação"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [cripto-monitor.frontend.components.header :as header]
            [cripto-monitor.frontend.components.sidebar :as sidebar]
            [cripto-monitor.frontend.components.crypto-card :as crypto-card]))

;; ===== ESTILOS ESPECÍFICOS DA VIEW PRINCIPAL =====
(defn main-styles []
  "Estilos específicos da view principal"
  [:style
   ".view-controls {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 20px;
      border-radius: 12px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
    }

    .view-options,
    .sort-options {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .view-options label,
    .sort-options label {
      font-weight: 600;
      color: #75777aff;
      font-size: 14px;
    }

    .view-buttons {
      display: flex;
      gap: 5px;
    }

    .view-btn {
      padding: 8px 16px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      cursor: pointer;
      font-size: 13px;
      font-weight: 500;
      transition: all 0.2s ease;
    }

    .view-btn:hover,
    .view-btn.active {
      background: #667eea;
      color: white;
      border-color: #667eea;
    }

    .sort-select {
      padding: 8px 12px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      font-size: 14px;
      cursor: pointer;
    }

    .sort-order-btn {
      width: 32px;
      height: 32px;
      border: 1px solid #e2e8f0;
      background: white;
      border-radius: 8px;
      cursor: pointer;
      font-size: 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.2s ease;
    }

    .sort-order-btn:hover {
      background: #f8fafc;
      border-color: #667eea;
    }

    .market-overview {
      margin-bottom: 20px;
      margin-top: 20px;
    }

    .overview-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
      gap: 20px;
      margin-bottom: 20px;
    }

    .overview-card {
      display: flex;
      flex: 1;
      border-radius: 12px;
      padding: 16px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
      align-items: center;
      gap: 10px;
      transition: transform 0.2s ease;
    }

    .overview-card:hover {
      transform: translateY(-2px);
    }

    .card-icon {
      font-size: 32px;
      width: 60px;
      height: 60px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #667eea, #764ba2);
      border-radius: 12px;
    }

    .card-content h3 {
      font-size: 14px;
      color: #64748b;
      margin-bottom: 8px;
      font-weight: 500;
    }

    .card-content h3:first-child {
      margin-top: 0;
    }

    .card-content h3:last-child {
      margin-bottom: 0;
    }

    .card-content {
      flex: 1;
    }

    .card-value {
      font-size: 24px;
      font-weight: 700;
      color: #8c929dff;
    }

    .card-value.small {
      font-size: 16px;
    }

    .card-value.positive {
      color: #10b981;
    }

    .card-value.negative {
      color: #ef4444;
    }

    .top-movers {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
      gap: 20px;
    }

    .movers-section {
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
    }

    .movers-section h4 {
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 16px;
    }

    .movers-list {
      space-y: 8px;
    }

    .mover-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px 0;
      border-bottom: 1px solid #f8fafc;
    }

    .mover-item:last-child {
      border-bottom: none;
    }

    .mover-symbol {
      font-weight: 600;

    }

    .mover-change {
      font-weight: 600;
      font-size: 14px;
    }

    .mover-change.positive {
      color: #10b981;
    }

    .mover-change.negative {
      color: #ef4444;
    }

    .crypto-section {
      display: flex;
      flex-direction: column;
      border-radius: 12px;
      padding: 20px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
      gap: 10px;
      transition: transform 0.2s ease;
    }

    .section-title {
      font-size: 24px;
      font-weight: 700;
      margin-bottom: 24px;
      display: flex;
      align-items: center;
      gap: 10px;
    }

    @media (max-width: 768px) {
      .view-controls {
        flex-direction: column;
        gap: 15px;
        align-items: stretch;
      }

      .view-options,
      .sort-options {
        justify-content: center;
      }

      .overview-cards {
        grid-template-columns: 1fr;
        gap: 15px;
      }

      .overview-card {
        padding: 20px;
      }

      .card-icon {
        width: 50px;
        height: 50px;
        font-size: 24px;
      }

      .card-value {
        font-size: 20px;
      }

      .top-movers {
        grid-template-columns: 1fr;
      }

      .crypto-section {
        padding: 20px;
      }
    }"])

(defn view-controls []
  "Controles de visualização"
  (let [view-mode @(rf/subscribe [:view-mode])
        sort-config @(rf/subscribe [:sort-config])]
    [:div.view-controls
     [:div.view-options
      [:label "Visualização:"]
      [:div.view-buttons
       [:button.view-btn
        {:class (when (= view-mode :compact) "active")
         :on-click #(rf/dispatch [:set-view-mode :compact])}
        "📋 Compacta"]
       [:button.view-btn
        {:class (when (= view-mode :detailed) "active")
         :on-click #(rf/dispatch [:set-view-mode :detailed])}
        "📊 Detalhada"]]]

     [:div.sort-options
      [:label "Ordenar por:"]
      [:select.sort-select
       {:value (name (:sort-by sort-config))
        :on-change #(rf/dispatch [:set-sort
                                  (keyword (-> % .-target .-value))
                                  (:sort-order sort-config)])}
       [:option {:value "symbol"} "Símbolo"]
       [:option {:value "name"} "Nome"]
       [:option {:value "price"} "Preço"]
       [:option {:value "change"} "Variação 24h"]
       [:option {:value "market-cap"} "Market Cap"]]

      [:button.sort-order-btn
       {:on-click #(rf/dispatch [:set-sort
                                 (:sort-by sort-config)
                                 (if (= (:sort-order sort-config) :asc) :desc :asc)])}
       (if (= (:sort-order sort-config) :asc) "↑" "↓")]]]))

(defn market-overview-cards []
  "Cards de visão geral do mercado"
  (let [market-stats @(rf/subscribe [:market-stats])]
    [:div.market-overview
     [:div.overview-cards
      [:div.overview-card
       [:div.card-content
        [:h3 "💰 Market Cap Total"]
        [:span.card-value
         (str "$" (crypto-card/format-market-cap (:total-market-cap market-stats 0)))]]]

      [:div.overview-card
       [:div.card-content
        [:h3 "🔄 Última Atualização"]
        [:span.card-value.small @(rf/subscribe [:formatted-last-update])]]]]]))

(defn gainers-losers-cards []
  "Cards de maiores altas e baixas"
  (let [top-gainers @(rf/subscribe [:top-gainers])
        top-losers @(rf/subscribe [:top-losers])]
    [:div.gainers-losers-cards
     [:h3 "📊 Maiores Altas e Baixas"]
     (when (or (seq top-gainers) (seq top-losers))
       [:div.top-movers

        (when (seq top-gainers)
          [:div.movers-section
           [:h4.movers-title.positive "🚀 Maiores Altas"]
           [:div.movers-list
            (for [coin (take 3 top-gainers)]
              ^{:key (str (:id coin))}
              [:div.mover-item
               [:span.mover-symbol (:symbol coin)]
               [:span.mover-change.positive
                (str "+" (.toFixed (:change_24h_percent coin) 2) "%")]])]])

        (when (seq top-losers)
          [:div.movers-section
           [:h4.movers-title.negative "📉 Maiores Baixas"]
           [:div.movers-list
            (for [coin (take 3 top-losers)]
              ^{:key (str (:id coin))}
              [:div.mover-item
               [:span.mover-symbol (:symbol coin)]
               [:span.mover-change.negative
                (str (.toFixed (:change_24h_percent coin) 2) "%")]])]])])]))

(defn dashboard-content []
  "Conteúdo principal do dashboard"
  (let [has-data? @(rf/subscribe [:has-data?])
        loading? @(rf/subscribe [:loading?])
        error @(rf/subscribe [:error])]
    [:div.dashboard-content

    ;; Lista de criptomoedas
     [:div.crypto-section
      [:h2.section-title "💰 Criptomoedas"]
      [crypto-card/crypto-cards-container]]

     ;; Visão geral do mercado
     [market-overview-cards]

     ;; Maiores altas e baixas
     [gainers-losers-cards]

     ;; Controles de visualização
     (when has-data?
       [view-controls])]))

(defn main-panel []
  "Painel principal da aplicação"
  (let [loading? @(rf/subscribe [:loading?])
        has-data? @(rf/subscribe [:has-data?])]

    ;; Iniciar atualização automática quando há dados
    (when (and has-data? (not loading?))
      (rf/dispatch [:start-auto-refresh]))

    [:div.app-container
     ;; Incluir estilos dos componentes
     [main-styles]
     [header/header-styles]
     [sidebar/sidebar-styles]
     [crypto-card/crypto-card-styles]

     ;; Header
     [header/header]

     ;; Sidebar
     [sidebar/sidebar]

     ;; Conteúdo principal
     [:main.main-content
      [dashboard-content]]]))


