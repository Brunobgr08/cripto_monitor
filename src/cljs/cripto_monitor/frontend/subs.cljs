(ns cripto-monitor.frontend.subs
  "Subscriptions do Re-frame para acesso aos dados"
  (:require [re-frame.core :as rf]
            [clojure.string :as str]))

;; ===== SUBSCRIPTIONS BÁSICAS =====
(rf/reg-sub
 :loading?
 (fn [db _]
   (:loading? db)))

(rf/reg-sub
 :error
 (fn [db _]
   (:error db)))

(rf/reg-sub
 :connection-status
 (fn [db _]
   (:connection-status db)))

(rf/reg-sub
 :last-update
 (fn [db _]
   (:last-update db)))

;; ===== SUBSCRIPTIONS DE DADOS =====
(rf/reg-sub
 :coins
 (fn [db _]
   (:coins db)))

(rf/reg-sub
 :current-prices
 (fn [db _]
   (:current-prices db)))

(rf/reg-sub
 :market-overview
 (fn [db _]
   (:market-overview db)))

(rf/reg-sub
 :selected-coin
 (fn [db _]
   (:selected-coin db)))

(rf/reg-sub
 :price-history
 (fn [db [_ symbol]]
   (get-in db [:price-history symbol])))

;; ===== SUBSCRIPTIONS DE FILTROS =====
(rf/reg-sub
 :search-filter
 (fn [db _]
   (get-in db [:filters :search])))

(rf/reg-sub
 :sort-config
 (fn [db _]
   (select-keys (:filters db) [:sort-by :sort-order])))

;; ===== SUBSCRIPTIONS COMPUTADAS =====
(rf/reg-sub
 :coins-with-prices
 :<- [:coins]
 :<- [:current-prices]
 (fn [[coins prices] _]
   (map (fn [coin]
          (let [price-data (get prices (:symbol coin))]
            (merge price-data coin))) ; coin sobrescreve price-data, preservando :id
        coins)))

(rf/reg-sub
 :filtered-coins
 :<- [:coins-with-prices]
 :<- [:search-filter]
 :<- [:sort-config]
 (fn [[coins search-term sort-config] _]
   (let [filtered (if (str/blank? search-term)
                    coins
                    (filter (fn [coin]
                              (or (str/includes? (str/lower-case (:symbol coin))
                                                 (str/lower-case search-term))
                                  (str/includes? (str/lower-case (:name coin))
                                                 (str/lower-case search-term))))
                            coins))
         sorted (case (:sort-by sort-config)
                  :symbol (sort-by :symbol filtered)
                  :name (sort-by :name filtered)
                  :price (sort-by :price_usd filtered)
                  :change (sort-by :change_24h_percent filtered)
                  :market-cap (sort-by :market_cap filtered)
                  filtered)]
     (if (= (:sort-order sort-config) :desc)
       (reverse sorted)
       sorted))))

(rf/reg-sub
 :top-gainers
 :<- [:coins-with-prices]
 (fn [coins _]
   (->> coins
        (filter :change_24h_percent)
        (sort-by :change_24h_percent >)
        (take 5))))

(rf/reg-sub
 :top-losers
 :<- [:coins-with-prices]
 (fn [coins _]
   (->> coins
        (filter :change_24h_percent)
        (sort-by :change_24h_percent <)
        (take 5))))

(rf/reg-sub
 :market-stats
 :<- [:coins-with-prices]
 :<- [:market-overview]
 (fn [[coins market-overview] _]
   (let [total-coins (count coins)
         coins-with-prices (filter :price_usd coins)
         total-market-cap (reduce + 0 (map #(or (:market_cap %) 0) coins-with-prices))
         avg-change (if (seq coins-with-prices)
                      (/ (reduce + 0 (map #(or (:change_24h_percent %) 0) coins-with-prices))
                         (count coins-with-prices))
                      0)]
     {:total-coins total-coins
      :coins-with-data (count coins-with-prices)
      :total-market-cap total-market-cap
      :avg-change avg-change
      :overview market-overview})))

(rf/reg-sub
 :coin-by-symbol
 :<- [:coins-with-prices]
 (fn [coins [_ symbol]]
   (first (filter #(= (:symbol %) symbol) coins))))

;; ===== SUBSCRIPTIONS DE STATUS =====
(rf/reg-sub
 :is-online?
 :<- [:connection-status]
 (fn [status _]
   (= status :online)))

(rf/reg-sub
 :is-loading?
 :<- [:loading?]
 (fn [loading? _]
   loading?))

(rf/reg-sub
 :has-data?
 :<- [:coins]
 :<- [:current-prices]
 (fn [[coins prices] _]
   (and (seq coins) (seq prices))))

;; ===== SUBSCRIPTIONS DE FORMATAÇÃO =====
(rf/reg-sub
 :formatted-last-update
 :<- [:last-update]
 (fn [last-update _]
   (when last-update
     (let [now (js/Date.)
           diff (- (.getTime now) (.getTime last-update))
           seconds (Math/floor (/ diff 1000))
           minutes (Math/floor (/ seconds 60))]
       (cond
         (< seconds 60) (str seconds " segundos atrás")
         (< minutes 60) (str minutes " minutos atrás")
         :else (.toLocaleTimeString last-update "pt-BR"))))))

;; ===== SUBSCRIPTIONS DE WEBSOCKET =====
(rf/reg-sub
 :websocket-connected?
 (fn [db _]
   (some? (:websocket db))))

;; ===== SUBSCRIPTIONS DE ALERTAS (preparação) =====
(rf/reg-sub
 :alerts
 (fn [db _]
   (:alerts db)))

(rf/reg-sub
 :active-alerts
 :<- [:alerts]
 (fn [alerts _]
   (filter :enabled alerts)))

;; ===== SUBSCRIPTIONS DE UI =====
(rf/reg-sub
 :view-mode
 (fn [db _]
   (get-in db [:ui :view-mode] :detailed)))

(rf/reg-sub
 :sidebar-collapsed?
 (fn [db _]
   (get-in db [:ui :sidebar-collapsed?] false)))

(rf/reg-sub
 :current-route
 (fn [db _]
   (get-in db [:ui :current-route] :dashboard)))

(rf/reg-sub
 :current-theme
 (fn [db _]
   (get-in db [:ui :theme] :light)))
