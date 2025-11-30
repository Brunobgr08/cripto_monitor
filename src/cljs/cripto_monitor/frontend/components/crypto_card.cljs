(ns cripto-monitor.frontend.components.crypto-card
  "Componente de card para exibir informações de criptomoedas"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

;; ===== ESTILOS ESPECÍFICOS =====
(defn crypto-card-styles []
  "Estilos específicos dos cards de crypto"
  [:style
   ".crypto-grid {
      display: grid;
      gap: 20px;
      margin-bottom: 10px;
    }

    .crypto-grid.view-compact {
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
    }

    .crypto-grid.view-detailed {
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
    }

    .crypto-card {
      border-radius: 16px;
      padding: 20px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
      transition: all 0.3s ease;
      cursor: pointer;
      position: relative;
      overflow: hidden;
    }

    .crypto-card::before {
      content: '';
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      height: 4px;
      background: linear-gradient(90deg, #667eea, #764ba2);
      transform: scaleX(0);
      transition: transform 0.3s ease;
    }

    .crypto-card:hover::before,
    .crypto-card.selected::before {
      transform: scaleX(1);
    }

    .crypto-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 8px 30px rgba(0, 0, 0, 0.15);
    }

    .crypto-card.selected {
      border: 2px solid #667eea;
      box-shadow: 0 8px 30px rgba(102, 126, 234, 0.2);
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 16px;
    }

    .coin-info {
      display: flex;
      flex-direction: column;
    }

    .coin-symbol {
      font-size: 18px;
      font-weight: 700;
      margin-bottom: 4px;
    }

    .coin-name {
      font-size: 14px;
      color: #64748b;
    }

    .change-indicator {
      display: flex;
      align-items: center;
      gap: 4px;
      padding: 6px 10px;
      border-radius: 8px;
      font-size: 13px;
      font-weight: 600;
    }

    .change-indicator.positive {
      background: #d1fae5;
      color: #065f46;
    }

    .change-indicator.negative {
      background: #fee2e2;
      color: #991b1b;
    }

    .change-arrow {
      font-size: 12px;
    }

    .price-section {
      margin-bottom: 16px;
    }

    .price {
      font-size: 24px;
      font-weight: 700;
      display: block;
      margin-bottom: 4px;
    }

    .market-cap {
      font-size: 12px;
      color: #64748b;
    }

    .price-trend {
      margin-top: 8px;
    }

    .stats-section {
      margin-bottom: 16px;
    }

    .stat-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }

    .stat-label {
      font-size: 12px;
      color: #64748b;
    }

    .stat-value {
      font-size: 13px;
      font-weight: 600;
    }

    .card-actions {
      display: flex;
      gap: 8px;
      justify-content: flex-end;
    }

    .action-btn {
      width: 32px;
      height: 32px;
      border: 1px solid #e2e8f0;
      background: white;
      border-radius: 8px;
      cursor: pointer;
      font-size: 14px;
      transition: all 0.2s ease;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .action-btn:hover {
      background: #f8fafc;
      border-color: #667eea;
      transform: scale(1.05);
    }

    .crypto-card.compact {
      padding: 16px;
    }

    .crypto-card.compact .price {
      font-size: 20px;
    }

    .crypto-card.compact .card-actions {
      display: none;
    }

    .loading-card {
      pointer-events: none;
    }

    .loading-shimmer {
      animation: shimmer 1.5s ease-in-out infinite;
    }

    .shimmer-line {
      height: 12px;
      background: #e2e8f0;
      border-radius: 6px;
      margin-bottom: 8px;
    }

    .shimmer-line.short {
      width: 60%;
    }

    .shimmer-line.medium {
      width: 80%;
    }

    .shimmer-line.long {
      width: 100%;
    }

    @keyframes shimmer {
      0% { opacity: 1; }
      50% { opacity: 0.5; }
      100% { opacity: 1; }
    }

    .empty-state {
      text-align: center;
      padding: 60px 20px;
      color: #64748b;
    }

    .empty-icon {
      font-size: 48px;
      margin-bottom: 16px;
    }

    .empty-state h3 {
      font-size: 20px;
      margin-bottom: 8px;
      color: #374151;
    }

    .empty-state p {
      margin-bottom: 20px;
    }

    .retry-btn {
      padding: 10px 20px;
      background: #667eea;
      color: white;
      border: none;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 500;
      transition: background 0.2s ease;
    }

    .retry-btn:hover {
      background: #5a67d8;
    }

    @media (max-width: 768px) {
      .crypto-grid {
        grid-template-columns: 1fr;
        gap: 15px;
      }

      .crypto-card {
        padding: 16px;
      }

      .price {
        font-size: 20px !important;
      }

      .card-actions {
        justify-content: center;
      }
    }"])

(defn format-price [price]
  "Formata preço para exibição"
  (when price
    (if (>= price 1)
      (.toLocaleString price "pt-BR" #js {:minimumFractionDigits 2 :maximumFractionDigits 2})
      (.toFixed price 6))))

(defn format-market-cap [market-cap]
  "Formata market cap para exibição"
  (when market-cap
    (cond
      (>= market-cap 1e12) (str (.toFixed (/ market-cap 1e12) 2) "T")
      (>= market-cap 1e9) (str (.toFixed (/ market-cap 1e9) 2) "B")
      (>= market-cap 1e6) (str (.toFixed (/ market-cap 1e6) 2) "M")
      (>= market-cap 1e3) (str (.toFixed (/ market-cap 1e3) 2) "K")
      :else (.toLocaleString market-cap "pt-BR"))))

(defn format-volume [volume]
  "Formata volume para exibição"
  (format-market-cap volume))

(defn change-indicator [change-percent]
  "Indicador visual de mudança de preço"
  (when change-percent
    (let [is-positive? (>= change-percent 0)
          formatted-change (.toFixed (Math/abs change-percent) 2)]
      [:div.change-indicator
       {:class (if is-positive? "positive" "negative")}
       [:span.change-arrow (if is-positive? "↗" "↘")]
       [:span.change-value (str (when is-positive? "+") formatted-change "%")]])))

(defn price-trend-mini [price-history]
  "Mini gráfico de tendência de preço"
  (when (seq price-history)
    (let [prices (map :price_usd (take-last 10 price-history))
          min-price (apply min prices)
          max-price (apply max prices)
          range (- max-price min-price)
          points (map-indexed
                  (fn [idx price]
                    (let [x (* idx 10)
                          y (if (zero? range)
                              50
                              (* 100 (- 1 (/ (- price min-price) range))))]
                      (str x "," y)))
                  prices)]
      [:div.price-trend
       [:svg {:width "90" :height "30" :viewBox "0 0 90 100"}
        [:polyline {:points (clojure.string/join " " points)
                    :fill "none"
                    :stroke (if (>= (last prices) (first prices)) "#10b981" "#ef4444")
                    :stroke-width "2"}]]])))

(defn crypto-card-compact [{:keys [symbol name price_usd change_24h_percent market_cap volume_24h]}]
  "Versão compacta do card de criptomoeda"
  [:div.crypto-card.compact
   {:on-click #(rf/dispatch [:select-coin symbol])}
   [:div.card-header
    [:div.coin-info
     [:span.coin-symbol symbol]
     [:span.coin-name name]]
    [change-indicator change_24h_percent]]

   [:div.card-body
    [:div.price-section
     [:span.price (str "$" (format-price price_usd))]
     (when market_cap
       [:span.market-cap (str "Cap: $" (format-market-cap market_cap))])]]])

(defn crypto-card-detailed [{:keys [symbol name price_usd change_24h_percent market_cap volume_24h] :as coin}]
  "Versão detalhada do card de criptomoeda"
  (let [price-history @(rf/subscribe [:price-history symbol])
        selected? @(rf/subscribe [:selected-coin])]
    [:div.crypto-card.detailed
     {:class (when (= selected? symbol) "selected")
      :on-click #(rf/dispatch [:select-coin symbol])}

     ;; Header com símbolo e mudança
     [:div.card-header
      [:div.coin-info
       [:span.coin-symbol symbol]
       [:span.coin-name name]]
      [change-indicator change_24h_percent]]

     ;; Preço principal
     [:div.price-section
      [:span.price (str "$" (format-price price_usd))]
      [price-trend-mini (:history price-history)]]

     ;; Estatísticas
     [:div.stats-section
      [:div.stat-item
       [:span.stat-label "Market Cap"]
       [:span.stat-value (if market_cap
                           (str "$" (format-market-cap market_cap))
                           "N/A")]]
      [:div.stat-item
       [:span.stat-label "Volume 24h"]
       [:span.stat-value (if volume_24h
                           (str "$" (format-volume volume_24h))
                           "N/A")]]]

     ;; Ações rápidas
     [:div.card-actions
      [:button.action-btn
       {:on-click (fn [e]
                    (.stopPropagation e)
                    (rf/dispatch [:fetch-price-history symbol 7]))
        :title "Ver histórico"}
       "📊"]
      [:button.action-btn
       {:on-click (fn [e]
                    (.stopPropagation e)
                    (rf/dispatch [:create-alert symbol]))
        :title "Criar alerta"}
       "🔔"]
      [:button.action-btn
       {:on-click (fn [e]
                    (.stopPropagation e)
                    (rf/dispatch [:add-to-portfolio symbol]))
        :title "Adicionar ao portfolio"}
        "💼"]
      [:button.action-btn
       {:on-click (fn [e]
                    (.stopPropagation e)
                    (rf/dispatch [:websocket-subscribe-coin symbol]))
        :title "Subscrever atualizações em tempo real"}
       "📡"]]]))

(defn crypto-grid [coins view-mode]
  "Grid de cards de criptomoedas"
  [:div.crypto-grid
   {:class (str "view-" (name view-mode))}
   (for [coin coins]
     (case view-mode
       :compact [:<> {:key (:id coin)} [crypto-card-compact coin]]
       :detailed [:<> {:key (:id coin)} [crypto-card-detailed coin]]
       [:<> {:key (:id coin)} [crypto-card-detailed coin]]))])

(defn loading-card []
  "Card de loading"
  [:div.crypto-card.loading
   [:div.loading-shimmer
    [:div.shimmer-line.short]
    [:div.shimmer-line.medium]
    [:div.shimmer-line.long]]])

(defn empty-state []
  "Estado vazio quando não há dados"
  [:div.empty-state
   [:div.empty-icon "🔍"]
   [:h3 "Nenhuma moeda encontrada"]
   [:p "Tente ajustar os filtros de busca ou verifique a conexão."]
   [:button.retry-btn
    {:on-click #(rf/dispatch [:fetch-current-prices])}
    "🔄 Tentar novamente"]])

(defn crypto-cards-container []
  "Container principal dos cards de criptomoedas"
  (let [coins @(rf/subscribe [:filtered-coins])
        loading? @(rf/subscribe [:loading?])
        view-mode @(rf/subscribe [:view-mode])]
    [:div.crypto-cards-container
     (cond
       loading? [:div.loading-grid
                 (for [i (range 6)]
                   ^{:key (str "loading-" i)}
                   [loading-card])]
       (empty? coins) [empty-state]
       :else [crypto-grid coins (or view-mode :detailed)])]))