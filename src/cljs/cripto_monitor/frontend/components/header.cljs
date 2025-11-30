(ns cripto-monitor.frontend.components.header
  "Componente do cabeçalho da aplicação"
  (:require [reagent.core :as r]
            [re-frame.core :as rf]))

;; ===== ESTILOS ADICIONAIS (via CSS-in-JS ou classes) =====
(defn header-styles []
  "Estilos específicos do header (se necessário)"
  [:style
   ".header {
      position: sticky;
      top: 0;
      z-index: 1000;
      backdrop-filter: blur(10px);
      border-bottom: 1px solid rgba(255, 255, 255, 0.1);
    }

    .search-container {
      position: relative;
      flex: 1;
      max-width: 400px;
      margin: 0 20px;
    }

    .search-input {
      width: 100%;
      padding: 10px 40px 10px 15px;
      border: 1px solid #e2e8f0;
      border-radius: 25px;
      font-size: 14px;
      outline: none;
      transition: all 0.2s ease;
    }

    .search-input:focus {
      border-color: #667eea;
      box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
    }

    .search-icon {
      position: absolute;
      right: 15px;
      top: 50%;
      transform: translateY(-50%);
      color: #64748b;
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 20px;
    }

    .market-summary {
      display: flex;
      gap: 15px;
    }

    .market-stat {
      display: flex;
      flex-direction: column;
      align-items: center;
      font-size: 12px;
    }

    .stat-label {
      color: #64748b;
      margin-bottom: 2px;
    }

    .stat-value {
      font-weight: 600;
      font-size: 14px;
    }

    .stat-value.positive {
      color: #10b981;
    }

    .stat-value.negative {
      color: #ef4444;
    }

    .header-actions {
      display: flex;
      gap: 10px;
    }

    .action-button {
      width: 36px;
      height: 36px;
      border: none;
      background: rgba(255, 255, 255, 0.1);
      border-radius: 8px;
      cursor: pointer;
      font-size: 16px;
      transition: all 0.2s ease;
    }

    .action-button:hover {
      background: rgba(255, 255, 255, 0.2);
      transform: scale(1.05);
    }

    .loading-bar {
      position: absolute;
      bottom: 0;
      left: 0;
      height: 2px;
      background: linear-gradient(90deg, #667eea, #764ba2);
      animation: loading-slide 2s ease-in-out infinite;
    }

    @keyframes loading-slide {
      0% { width: 0%; }
      50% { width: 70%; }
      100% { width: 100%; }
    }

    .error-notification {
      position: absolute;
      top: 100%;
      left: 0;
      right: 0;
      background: #fee2e2;
      color: #991b1b;
      padding: 10px 20px;
      display: flex;
      align-items: center;
      gap: 10px;
      border-bottom: 1px solid #fecaca;
    }

    .error-message {
      flex: 1;
      font-size: 14px;
    }

    .error-close {
      background: none;
      border: none;
      color: #991b1b;
      cursor: pointer;
      font-size: 16px;
      padding: 0;
      width: 20px;
      height: 20px;
    }

    @media (max-width: 768px) {
      .market-summary {
        display: none;
      }

      .search-container {
        max-width: 200px;
        margin: 0 10px;
      }

      .header-right {
        gap: 10px;
      }
    }"])

(defn connection-status-indicator []
  "Indicador de status da conexão"
  (let [status @(rf/subscribe [:connection-status])
        ;; last-update @(rf/subscribe [:formatted-last-update])
        ]
    [:div.header-status
     [:div.status-indicator
      {:class (name status)}
      [:div.status-dot {:class (name status)}]
      [:span (case status
               :online "Online"
               :offline "Offline"
               :connecting "Conectando..."
               "Desconhecido")]]
    ;;  (when last-update
    ;;    [:div.last-update
    ;;     [:span "Última atualização: " last-update]])
        ]))

(defn search-bar []
  "Barra de pesquisa"
  (let [search-term @(rf/subscribe [:search-filter])]
    [:div.search-container
     [:input.search-input
      {:type "text"
       :placeholder "Buscar moedas..."
       :value search-term
       :on-change #(rf/dispatch [:set-search-filter (-> % .-target .-value)])}]
     [:div.search-icon "🔍"]]))

;; (defn market-summary []
;;   "Resumo rápido do mercado"
;;   (let [market-stats @(rf/subscribe [:market-stats])]
;;     [:div.market-summary
;;      [:div.market-stat
;;       [:span.stat-label "Moedas"]
;;       [:span.stat-value (:total-coins market-stats 0)]]
;;      [:div.market-stat
;;       [:span.stat-label "Variação Média"]
;;       [:span.stat-value
;;        {:class (if (pos? (:avg-change market-stats 0)) "positive" "negative")}
;;        (str (if (pos? (:avg-change market-stats 0)) "+" "")
;;             (.toFixed (:avg-change market-stats 0) 2) "%")]]]))

(defn header-actions []
  "Ações do cabeçalho"
  (let [current-theme @(rf/subscribe [:current-theme])]
    [:div.header-actions
     [:button.action-button
      {:on-click #(rf/dispatch [:fetch-current-prices])
       :title "Atualizar dados"}
      "🔄"]
     [:button.action-button
      {:on-click #(rf/dispatch [:toggle-fullscreen])
       :title "Tela cheia"}
      "⛶"]
     [:button.action-button
      {:on-click #(rf/dispatch [:toggle-theme])
       :title (str "Alternar tema (atual: " (name current-theme) ")")}
      (if (= current-theme :dark) "☀️" "🌙")]]))

(defn header []
  "Componente principal do cabeçalho"
  (let [loading? @(rf/subscribe [:loading?])
        error @(rf/subscribe [:error])]
    [:header.header
     [header-styles]

     ;; Logo e título
     [:div.header-logo
      [:span.emoji "🚀"]
      [:span "Cripto Monitor"]]

     ;; Barra de pesquisa (centro)
     [search-bar]

     ;; Status e ações (direita)
     [:div.header-right
      ;; [market-summary]
      [connection-status-indicator]
      [header-actions]]

     ;; Indicador de loading
     (when loading?
       [:div.loading-bar])

     ;; Notificação de erro
     (when error
       [:div.error-notification
        [:span.error-icon "⚠️"]
        [:span.error-message error]
        [:button.error-close
         {:on-click #(rf/dispatch [:clear-error])}
         "✕"]])]))