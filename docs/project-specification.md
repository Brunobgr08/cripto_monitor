# Especificação Técnica - Cripto Monitor

## 📋 Issues Detalhadas

### ✅ Issue 1: Setup do Projeto e Configuração Inicial - **COMPLETA**

**Duração**: 3 dias
**Prioridade**: Alta

**Objetivos**:

- Criar estrutura de projeto Clojure
- Configurar deps.edn com dependências
- Setup de ambiente de desenvolvimento containerizado
- Estrutura de diretórios
- Configuração Docker para desenvolvimento

**Entregáveis**:

```
cripto-monitor/
├── Dockerfile
├── docker-compose.yml
├── docker-compose.dev.yml
├── .dockerignore
├── deps.edn
├── src/
│   └── cripto_monitor/
│       ├── core.clj
│       ├── config.clj
│       └── utils.clj
├── test/
├── resources/
│   └── config.edn
├── dev/
│   └── user.clj
└── scripts/
    ├── dev-setup.sh
    └── build.sh
```

**Dependências (deps.edn)**:

```clojure
{:deps {org.clojure/clojure {:mvn/version "1.11.1"}
        org.clojure/core.async {:mvn/version "1.6.673"}
        metosin/reitit {:mvn/version "0.7.0"}
        ring/ring-core {:mvn/version "1.10.0"}
        cheshire/cheshire {:mvn/version "5.12.0"}
        com.github.seancorfield/next.jdbc {:mvn/version "1.3.909"}
        org.postgresql/postgresql {:mvn/version "42.7.1"}
        tick/tick {:mvn/version "0.6.2"}
        com.taoensso/timbre {:mvn/version "6.3.1"}}}
```

### ✅ Issue 2: Configuração do Banco de Dados - **COMPLETA**

**Duração**: 4 dias
**Prioridade**: Alta

**Objetivos**:

- Setup PostgreSQL via Docker Compose
- Criação de schemas e tabelas
- Configuração de conexão containerizada
- Migrations básicas
- Volumes persistentes para dados
- Configuração de rede Docker

**Schema SQL**:

```sql
-- Tabela de moedas
CREATE TABLE coins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    coingecko_id VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Histórico de preços
CREATE TABLE price_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coin_id UUID REFERENCES coins(id),
    price_usd DECIMAL(20,8) NOT NULL,
    market_cap BIGINT,
    volume_24h BIGINT,
    change_24h_percent DECIMAL(10,4),
    collected_at TIMESTAMP NOT NULL,
    source VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- Índices para performance
CREATE INDEX idx_price_history_coin_time ON price_history(coin_id, collected_at DESC);
CREATE INDEX idx_price_history_collected_at ON price_history(collected_at DESC);
```

### ✅ Issue 3: Cliente API Externa - **COMPLETA**

**Duração**: 5 dias
**Prioridade**: Alta

**Objetivos**:

- Cliente HTTP para CoinGecko API
- Tratamento de rate limiting
- Retry logic e circuit breaker
- Parsing de respostas JSON

**Funcionalidades**:

```clojure
(ns cripto-monitor.api.client
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [taoensso.timbre :as log]))

(defn fetch-coin-prices
  "Busca preços atuais de múltiplas moedas"
  [coin-ids]
  ;; Implementar chamada para CoinGecko
  )

(defn fetch-coin-history
  "Busca histórico de uma moeda específica"
  [coin-id days]
  ;; Implementar busca histórica
  )
```

### ✅ Issue 4: Sistema de Coleta de Dados - **COMPLETA**

**Duração**: 6 dias
**Prioridade**: Alta

**Objetivos**:

- Implementar core.async channels
- Scheduler para coleta periódica
- Workers para processamento
- Buffer e backpressure handling

**Arquitetura core.async**:

```clojure
(ns cripto-monitor.collector.core
  (:require [clojure.core.async :as async]
            [tick.core :as t]))

;; Channels principais
(def price-channel (async/chan 1000))
(def processing-channel (async/chan 100))
(def storage-channel (async/chan 50))

;; Scheduler
(defn start-collection-scheduler
  "Inicia coleta a cada X segundos"
  [interval-seconds]
  ;; Implementar scheduler
  )

;; Workers
(defn start-price-processor
  "Worker para processar dados de preço"
  []
  ;; Implementar processamento
  )
```

### ✅ Issue 5: Camada de Persistência - **COMPLETA**

**Duração**: 4 dias
**Prioridade**: Alta

**Objetivos**:

- ✅ Funções CRUD com next.jdbc
- ✅ Queries otimizadas
- ✅ Connection pooling
- ✅ Transações

**Interface de Dados**:

```clojure
(ns cripto-monitor.db.core
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]))

(defn insert-price-data!
  "Insere dados de preço no banco"
  [db price-data]
  ;; Implementar inserção
  )

(defn get-current-prices
  "Busca preços mais recentes"
  [db coin-ids]
  ;; Implementar query
  )

(defn get-price-history
  "Busca histórico de preços"
  [db coin-id from-date to-date]
  ;; Implementar query histórica
  )
```

### ✅ Issue 6: API REST - Endpoints Básicos - **COMPLETA**

**Duração**: 5 dias
**Prioridade**: Média

**Objetivos**:

- ✅ Setup Ring + Reitit
- ✅ Endpoints básicos de consulta
- ✅ Middleware de logging e CORS
- ✅ Documentação OpenAPI/Swagger

**Endpoints**:

```clojure
;; GET /api/health - Health check
;; GET /api/coins - Lista moedas disponíveis
;; GET /api/coins/:symbol - Detalhes de uma moeda
;; GET /api/search/coins - Buscar moedas
;; GET /api/prices/current - Preços atuais
;; GET /api/prices/current/:symbol - Preço atual de uma moeda
;; GET /api/prices/history/:symbol - Histórico de preços
;; GET /api/market/overview - Visão geral do mercado
;; GET /api/market/gainers - Top gainers
;; GET /api/market/losers - Top losers
;; GET /api/stats/:symbol - Estatísticas da moeda
;; GET /api/system/status - Status do sistema
;; POST /api/system/collect - Forçar coleta de dados
```

### ✅ Issue 7: Sistema de Alertas - **COMPLETA**

**Duração**: 6 dias
**Prioridade**: Média

**Objetivos**:

- Engine de alertas configuráveis
- Múltiplos tipos de notificação
- Throttling de alertas
- Persistência de configurações

**Tipos de Alertas**:

- Preço acima/abaixo de threshold
- Variação percentual em período
- Volume anômalo
- Múltiplas moedas correlacionadas

### ✅ Issue 8: API REST - Endpoints Avançados - **COMPLETA**

**Duração**: 4 dias
**Prioridade**: Média

**Objetivos**:

- Estatísticas e agregações
- Endpoints de configuração
- Filtros avançados
- Paginação

**Endpoints Avançados**:

```clojure
;; GET /api/alerts - Lista alertas do usuário
;; POST /api/alerts - Criar novo alerta
;; GET /api/alerts/:alert-id - Detalhes de um alerta
;; PUT /api/alerts/:alert-id - Atualizar alerta
;; DELETE /api/alerts/:alert-id - Remover alerta
;; GET /api/analytics/correlation - Correlação entre moedas
;; POST /api/analytics/portfolio - Performance de portfolio
;; GET /api/binance/ticker - Ticker 24h Binance
;; GET /api/binance/klines/:symbol - Candlestick data
;; GET /api/binance/orderbook/:symbol - Order book data
```

### ✅ Issue 9: Frontend ClojureScript - **COMPLETA**

**Duração**: 8 dias
**Prioridade**: Baixa

**Objetivos**:

- Dashboard responsivo
- Gráficos de preços
- Interface de alertas
- Atualização em tempo real

**Tecnologias Frontend**:

- ClojureScript + Shadow-cljs
- Reagent para componentes
- Re-frame para estado
- Recharts para gráficos

### ✅ Issue 10: WebSockets e Tempo Real

**Duração**: 5 dias
**Prioridade**: Baixa

**Objetivos**:

- WebSocket server
- Push de atualizações
- Reconexão automática
- Filtros de subscrição

### ✅ Issue 11: Testes e Documentação

**Duração**: 6 dias
**Prioridade**: Alta

**Objetivos**:

- Testes unitários (clojure.test)
- Testes de integração
- Documentação da API
- Guias de setup

### ✅ Issue 12: Deploy e Monitoramento

**Duração**: 4 dias
**Prioridade**: Média

**Objetivos**:

- Docker Compose para produção
- Deploy automatizado com CI/CD
- Logging estruturado (ELK Stack)
- Métricas e alertas (Prometheus/Grafana)
- Health checks e auto-restart
- Backup automatizado do banco

## 🎯 Cronograma Resumido

| Semana | Fase     | Issues | Foco                    |
| ------ | -------- | ------ | ----------------------- |
| 1      | Fundação | 1-2    | Setup + Docker + DB     |
| 2-3    | Coleta   | 3-5    | APIs + Async + Cache    |
| 4-5    | Backend  | 6-8    | REST + Alertas + Redis  |
| 6-7    | Frontend | 9-10   | UI + WebSockets + Nginx |
| 8      | Deploy   | 11-12  | CI/CD + Monitoring      |

## 📊 Métricas de Sucesso

- ✅ Coleta de dados < 30s de latência
- ✅ API responde < 200ms (95th percentile)
- ✅ Uptime > 99.5%
- ✅ Alertas entregues < 60s
- ✅ Dashboard atualiza < 5s
