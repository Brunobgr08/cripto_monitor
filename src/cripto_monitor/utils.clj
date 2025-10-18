(ns cripto-monitor.utils
  "Funções utilitárias para o Cripto Monitor"
  (:require [tick.core :as t]
            [cheshire.core :as json]
            [taoensso.timbre :as log]
            [clojure.string :as str]))

;; Utilitários de Tempo
(defn now
  "Retorna o timestamp atual"
  []
  (t/now))

(defn format-timestamp
  "Formata timestamp para armazenamento em banco de dados. Padrão ISO 8601."
  [instant]
  (t/format (t/formatter "yyyy-MM-dd'T'HH:mm:ss.SSSX") instant))

(defn parse-timestamp
  "Converte timestamp para string"
  [timestamp-str]
  (t/instant timestamp-str))

(defn minutes-ago
  "Retorna o timestamp N minutos atrás"
  [minutes]
  (t/>> (now) (t/new-duration (- minutes) :minutes)))

(defn hours-ago
  "Retorna o timestamp N horas atrás"
  [hours]
  (t/>> (now) (t/new-duration (- hours) :hours)))

(defn days-ago
  "Retorna o timestamp N dias atrás"
  [days]
  (t/>> (now) (t/new-duration (- days) :days)))

;; Utilitários de JSON
(defn ->json
  "Converte dados para string JSON"
  [data]
  (json/generate-string data {:pretty true}))

(defn <-json
  "Converte string JSON para dados"
  [json-str]
  (json/parse-string json-str true))

;; Utilitários de String
(defn normalize-symbol
  "Padroniza símbolo de criptomoeda para maiúsculo e remove espaços"
  [symbol]
  (-> symbol
      str/upper-case
      str/trim))

(defn normalize-coin-id
  "Padroniza ID de criptomoeda para minúsculo e remove espaços"
  [coin-id]
  (-> coin-id
      str/lower-case
      str/trim))

;; Utilitários de Número
(defn safe-parse-double
  "Converte string para double"
  [s]
  (try
    (Double/parseDouble (str s))
    (catch Exception _
      nil)))

(defn format-currency
  "Formata número como moeda"
  [amount]
  (format "%.2f" (double amount)))

(defn format-percentage
  "Formata número como porcentagem"
  [percentage]
  (format "%.2f%%" (double percentage)))

(defn calculate-percentage-change
  "Calcula porcentagem de mudança entre valores antigo e novo"
  [old-value new-value]
  (when (and old-value new-value (not= old-value 0))
    (* 100.0 (/ (- new-value old-value) old-value))))

;; Utilitários de Validação
(defn valid-email?
  "Verifica se string é um email válido"
  [email]
  (and email
       (string? email)
       (re-matches #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$" email)))

(defn valid-url?
  "Verifica se string é uma URL válida"
  [url]
  (and url
       (string? url)
       (re-matches #"^https?://.*" url)))

(defn valid-symbol?
  "Verifica se string é um símbolo de criptomoeda válido"
  [symbol]
  (and symbol
       (string? symbol)
       (>= (count symbol) 3)
       (<= (count symbol) 10)
       (re-matches #"^[A-Z]{3,10}$" (str/upper-case symbol))))

;; Tratamento de Erro
(defn safe-execute
  "Executa função com segurança e registra erros"
  [f & args]
  (try
    (apply f args)
    (catch Exception e
      (log/error e "Error executing function" {:function f :args args})
      nil)))

(defn retry-with-backoff
  "Executa função com retentativas com backoff exponencial"
  [f max-attempts initial-delay-ms]
  (loop [attempt 1
         delay initial-delay-ms]
    (let [result (try
                   {:success true :result (f)}
                   (catch Exception e
                     {:success false :error e}))]
      (if (:success result)
        (:result result)
        (if (< attempt max-attempts)
          (do
            (log/warn "Attempt" attempt "failed, retrying in" delay "ms" {:error (:error result)})
            (Thread/sleep delay)
            (recur (inc attempt) (* delay 2)))
          (do
            (log/error "All retry attempts failed" {:attempts max-attempts :error (:error result)})
            (throw (:error result))))))))

;; Utilitários de Coleção
(defn remove-nil-values
  "Remove valores nulos de um mapa"
  [m]
  (into {} (filter (comp some? val) m)))

(defn deep-merge
  "Mescla mapas de forma profunda"
  [& maps]
  (apply merge-with
         (fn [x y]
           (if (and (map? x) (map? y))
             (deep-merge x y)
             y))
         maps))

(comment
  ;; Funções auxiliares de desenvolvimento
  (now)
  (format-timestamp (now))
  (minutes-ago 30)
  (normalize-symbol "btc")
  (calculate-percentage-change 100 110)
  (valid-email? "test@example.com")
  (valid-symbol? "BTC"))
