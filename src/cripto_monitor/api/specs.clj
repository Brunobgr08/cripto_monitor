(ns cripto-monitor.api.specs
  "Especificações OpenAPI/Swagger para documentação da API"
  (:require [clojure.spec.alpha :as s]))

;; ============================================================================
;; SCHEMAS BÁSICOS
;; ============================================================================

;; Coin Schema
(s/def ::symbol string?)
(s/def ::name string?)
(s/def ::rank (s/and int? pos?))
(s/def ::price_usd (s/and number? (complement neg?)))
(s/def ::market_cap_usd (s/and number? (complement neg?)))
(s/def ::volume_24h_usd (s/and number? (complement neg?)))
(s/def ::percent_change_1h number?)
(s/def ::percent_change_24h number?)
(s/def ::percent_change_7d number?)
(s/def ::last_updated string?)

(s/def ::coin
  (s/keys :req-un [::symbol ::name ::rank ::price_usd ::market_cap_usd
                   ::volume_24h_usd ::percent_change_1h ::percent_change_24h
                   ::percent_change_7d ::last_updated]))

;; Health Check Schema
(s/def ::status #{"healthy" "unhealthy"})
(s/def ::database #{"healthy" "unhealthy"})
(s/def ::running boolean?)
(s/def ::collected (s/and int? (complement neg?)))
(s/def ::errors (s/and int? (complement neg?)))
(s/def ::last_collection string?)
(s/def ::uptime (s/and int? (complement neg?)))

(s/def ::collector-stats
  (s/keys :req-un [::collected ::errors ::last_collection]))

(s/def ::collector
  (s/keys :req-un [::running ::collector-stats]))

(s/def ::health-check
  (s/keys :req-un [::status ::database ::collector ::uptime]))

;; Response Schemas
(s/def ::success boolean?)
(s/def ::data any?)
(s/def ::message string?)
(s/def ::error string?)

(s/def ::success-response
  (s/keys :req-un [::success ::data]))

(s/def ::error-response
  (s/keys :req-un [::success ::error]
          :opt-un [::message]))

;; Query Parameters
;; Common Parameters
(s/def ::q string?)
(s/def ::limit (s/and int? pos? #(<= % 100)))
(s/def ::offset (s/and int? (complement neg?)))
(s/def ::period #{"1h" "24h" "7d" "30d" "1y"})
(s/def ::interval #{"1h" "2h" "4h" "6h" "8h" "12h" "1d" "3d" "1w" "1M"})

;; Analytics Parameters
(s/def ::days (s/and int? pos?))
(s/def ::holdings (s/and int? pos?))

;; ============================================================================
;; SWAGGER METADATA
;; ============================================================================

(def swagger-info
  {:info {:title "Cripto Monitor API"
          :description "API REST para monitoramento de criptomoedas em tempo real"
          :version "1.0.0"
          :contact {:name "Cripto Monitor Team"
                    :email "contato@criptomonitor.com"}}
   :basePath ""
   :produces ["application/json"]
   :consumes ["application/json"]
   :tags [{:name "health" :description "🏥 Endpoints de saúde do sistema"}
          {:name "coins" :description "🪙 Informações sobre criptomoedas"}
          {:name "search" :description "🔍 Busca de criptomoedas"}
          {:name "prices" :description "💰 Preços e histórico de preços"}
          {:name "market" :description "📊 Visão geral do mercado"}
          {:name "analytics" :description "📈 Análises e estatísticas"}
          {:name "alerts" :description "🚨 Gerenciamento de alertas"}
          {:name "system" :description "⚙️ Controle e status do sistema"}
          {:name "binance" :description "🔗 Integração com Binance"}]})

;; ============================================================================
;; ROUTE DOCUMENTATION
;; ============================================================================

(def route-docs
  {;; Health endpoints
   ::health {:tags ["health"]
             :summary "Verificação de saúde do sistema"
             :description "Retorna o status de saúde do sistema, incluindo banco de dados e collector"
             :responses {200 {:description "Sistema saudável"}
                         503 {:description "Sistema com problemas"}}}

   ;; Coins endpoints
   ::all-coins {:tags ["coins"]
                :summary "Lista todas as criptomoedas"
                :description "Retorna lista completa de criptomoedas monitoradas"
                :parameters {:query (s/keys :opt-un [::limit ::offset])}
                :responses {200 {:description "Lista de criptomoedas"}}}

   ::coin-by-symbol {:tags ["coins"]
                     :summary "Busca criptomoeda por símbolo"
                     :description "Retorna informações detalhadas de uma criptomoeda específica"
                     :parameters {:path (s/keys :req-un [::symbol])}
                     :responses {200 {:description "Informações da criptomoeda"}
                                 404 {:description "Criptomoeda não encontrada"}}}

   ;; Search endpoints
   ::search-coins {:tags ["search"]
                   :summary "Busca criptomoedas"
                   :description "Busca criptomoedas por nome ou símbolo"
                   :parameters {:query (s/keys :req-un [::q]
                                               :opt-un [::limit])}
                   :responses {200 {:description "Resultados da busca"}}}

   ;; Prices endpoints
   ::current-prices {:tags ["prices"]
                     :summary "Preços atuais"
                     :description "Retorna preços atuais de todas as criptomoedas"
                     :parameters {:query (s/keys :opt-un [::limit ::offset])}
                     :responses {200 {:description "Preços atuais"}}}

   ::current-price-by-symbol {:tags ["prices"]
                              :summary "Preço atual por símbolo"
                              :description "Retorna preço atual de uma criptomoeda específica"
                              :parameters {:path (s/keys :req-un [::symbol])}
                              :responses {200 {:description "Preço atual"}
                                          404 {:description "Criptomoeda não encontrada"}}}

   ::price-history {:tags ["prices"]
                    :summary "Histórico de preços"
                    :description "Retorna histórico de preços de uma criptomoeda"
                    :parameters {:path (s/keys :req-un [::symbol])
                                 :query (s/keys :opt-un [::period ::limit])}
                    :responses {200 {:description "Histórico de preços"}
                                404 {:description "Criptomoeda não encontrada"}}}

   ;; Market endpoints
   ::market-overview {:tags ["market"]
                      :summary "Visão geral do mercado"
                      :description "Retorna estatísticas gerais do mercado de criptomoedas"
                      :responses {200 {:description "Visão geral do mercado"}}}

   ::top-gainers {:tags ["market"]
                  :summary "Maiores valorizações"
                  :description "Retorna criptomoedas com maiores valorizações"
                  :parameters {:query (s/keys :opt-un [::limit])}
                  :responses {200 {:description "Maiores valorizações"}}}

   ::top-losers {:tags ["market"]
                 :summary "Maiores desvalorizações"
                 :description "Retorna criptomoedas com maiores desvalorizações"
                 :parameters {:query (s/keys :opt-un [::limit])}
                 :responses {200 {:description "Maiores desvalorizações"}}}

   ;; System endpoints
   ::force-collection {:tags ["system"]
                       :summary "Força coleta de dados"
                       :description "Força uma nova coleta de dados das APIs externas"
                       :responses {200 {:description "Coleta iniciada com sucesso"}
                                   500 {:description "Erro ao iniciar coleta"}}}

   ::system-status {:tags ["system"]
                    :summary "Status do sistema"
                    :description "Retorna informações detalhadas sobre o status do sistema"
                    :responses {200 {:description "Status do sistema"}}}

   ;; Alerts endpoints
   ::list-alerts {:tags ["alerts"]
                  :summary "Lista de alertas"
                  :description "Retorna lista de alertas"
                  :parameters {:query (s/keys :opt-un [::user-id])}
                  :responses {200 {:description "Lista de alertas"}}}

   ::create-alert {:tags ["alerts"]
                   :summary "Cria um novo alerta"
                   :description "Cria um novo alerta"
                   :parameters {:body (s/keys :req-un [::coin-symbol ::alert-type ::params ::user-id]
                                              :opt-un [::enabled])}
                   :responses {200 {:description "Alerta criado com sucesso"}
                               400 {:description "Parâmetros inválidos"}
                               500 {:description "Erro interno do servidor"}}}

   ::get-alert {:tags ["alerts"]
                :summary "Obtém um alerta"
                :description "Obtém um alerta pelo ID"
                :parameters {:path (s/keys :req-un [::alert-id])}
                :responses {200 {:description "Alerta encontrado"}
                            404 {:description "Alerta não encontrado"}
                            500 {:description "Erro interno do servidor"}}}

   ::update-alert {:tags ["alerts"]
                   :summary "Atualiza um alerta"
                   :description "Atualiza um alerta pelo ID"
                   :parameters {:path (s/keys :req-un [::alert-id])
                                :body (s/keys :opt-un [::enabled ::params])}
                   :responses {200 {:description "Alerta atualizado com sucesso"}
                               400 {:description "Parâmetros inválidos"}
                               404 {:description "Alerta não encontrado"}
                               500 {:description "Erro interno do servidor"}}}

   ::delete-alert {:tags ["alerts"]
                   :summary "Remove um alerta"
                   :description "Remove um alerta pelo ID"
                   :parameters {:path (s/keys :req-un [::alert-id])}
                   :responses {200 {:description "Alerta removido com sucesso"}
                               404 {:description "Alerta não encontrado"}
                               500 {:description "Erro interno do servidor"}}}

   ;; Binance endpoints
   ::binance-ticker {:tags ["binance"]
                     :summary "Ticker 24h Binance"
                     :description "Retorna ticker 24h de uma ou mais criptomoedas"
                     :parameters {:query (s/keys :opt-un [::symbols])}
                     :responses {200 {:description "Ticker 24h"}
                                 500 {:description "Erro interno do servidor"}}}

   ::binance-klines {:tags ["binance"]
                     :summary "Candlestick data"
                     :description "Retorna dados de candlestick de uma criptomoeda"
                     :parameters {:path (s/keys :req-un [::symbol])
                                  :query (s/keys :opt-un [::interval ::limit])}
                     :responses {200 {:description "Dados de candlestick"}
                                 500 {:description "Erro interno do servidor"}}}

   ::binance-orderbook {:tags ["binance"]
                        :summary "Order book data"
                        :description "Retorna dados do livro de ofertas de uma criptomoeda"
                        :parameters {:path (s/keys :req-un [::symbol])
                                     :query (s/keys :opt-un [::limit])}
                        :responses {200 {:description "Dados do livro de ofertas"}
                                    500 {:description "Erro interno do servidor"}}}

   ;; Analytics endpoints
   ::price-correlation {:tags ["analytics"]
                        :summary "Correlação de preços"
                        :description "Retorna correlação de preços entre duas criptomoedas"
                        :parameters {:query (s/keys :req-un [::coin1 ::coin2]
                                                   :opt-un [::days])}
                        :responses {200 {:description "Correlação de preços"}
                                    500 {:description "Erro interno do servidor"}}}

   ::portfolio-performance {:tags ["analytics"]
                            :summary "Performance de portfolio"
                            :description "Retorna performance de um portfolio de criptomoedas"
                            :parameters {:body (s/keys :req-un [::holdings])}
                            :responses {200 {:description "Performance do portfolio"}
                                        500 {:description "Erro interno do servidor"}}}})

