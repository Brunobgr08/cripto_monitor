# 📈 Cripto Monitor

> Sistema completo de monitoramento de criptomoedas em tempo real desenvolvido em Clojure/ClojureScript

[![Clojure](https://img.shields.io/badge/Clojure-1.11.1-blue.svg)](https://clojure.org/)
[![ClojureScript](https://img.shields.io/badge/ClojureScript-1.12.42-blue.svg)](https://clojurescript.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-green.svg)](#-testes)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## 🚀 Visão Geral

O **Cripto Monitor** é uma plataforma completa para monitoramento, análise e alertas de criptomoedas em tempo real. Desenvolvido com tecnologias modernas e arquitetura robusta, oferece desde funcionalidades básicas para iniciantes até ferramentas avançadas para traders profissionais.

### ✨ Principais Funcionalidades

- 📊 **Monitoramento em Tempo Real** - Preços atualizados via WebSocket com 5 moedas principais
- 🚨 **Sistema de Alertas Inteligentes** - 4 tipos de alertas (preço, variação %, volume, correlação)
- 📈 **Dashboard Interativo** - Interface moderna com tema claro/escuro
- 🔌 **API REST Completa** - 20+ endpoints para todas as funcionalidades
- 🎨 **Interface Web Responsiva** - ClojureScript + Reagent + Re-frame
- 🐳 **Deploy Simplificado** - Containerização completa com Docker
- 🧪 **Cobertura de Testes** - 100% dos módulos principais testados

## 🛠️ Tecnologias

### Backend

#### **Core Framework**

- **Clojure 1.11.1** - Linguagem principal
- **Ring 1.10.0 + Reitit 0.7.0** - Framework web e roteamento
- **http-kit 2.7.0** - Servidor HTTP e WebSocket
- **core.async 1.6.681** - Programação assíncrona e canais

#### **Banco de Dados**

- **next.jdbc 1.3.909 + HikariCP 3.0.1** - Acesso ao banco de dados com pool de conexões
- **PostgreSQL 42.7.1** - Driver PostgreSQL

#### **API e Documentação**

- **reitit-swagger 0.7.0** - Geração de especificação OpenAPI
- **reitit-swagger-ui 0.7.0** - Interface Swagger UI interativa
- **reitit-spec 0.7.0** - Coerção com Clojure Spec
- **muuntaja 0.6.10** - Content negotiation e serialização

#### **Middleware e Utilitários**

- **ring-cors 0.1.13** - CORS middleware
- **Cheshire 5.12.0** - Serialização JSON
- **clj-http 3.12.3** - Cliente HTTP para integração com APIs externas

#### **Cache e Sessões**

- **carmine 3.2.0** - Cliente Redis

#### **Logging e Configuração**

- **Timbre 6.3.1** - Sistema de logs estruturados
- **slf4j-simple 2.0.9** - Logging SLF4J
- **Aero 1.1.6** - Gerenciamento de configurações

#### **Utilitários**

- **tick 0.6.2** - Manipulação de datas e horários
- **medley 1.4.0** - Utilitários para manipulação de coleções
- **prismatic/schema 1.4.1** - Definição de esquemas de dados

### Frontend

- **ClojureScript 1.12.42** - Clojure para JavaScript (via Shadow-cljs)
- **Reagent 1.2.0** - Componentes React funcionais
- **Re-frame 1.3.0** - Gerenciamento de estado (padrão Flux)
- **Shadow-cljs 2.25.10** - Build tool e hot-reload
- **React 18.2.0** - Biblioteca de componentes
- **Recharts 2.8.0** - Gráficos e visualizações interativas
- **Day8.re-frame/http-fx 0.2.4** - Efeitos HTTP para Re-frame
- **cljs-ajax 0.8.4** - Cliente HTTP para ClojureScript
- **binaryage/devtools 1.0.7** - Ferramentas de desenvolvimento

### Ferramentas de Desenvolvimento

#### **Build e Deploy**

- **tools.build 0.9.6** - Sistema de build moderno
- **migratus 1.5.3** - Migrações de banco de dados

#### **REPL e Desenvolvimento**

- **nrepl 1.0.0** - REPL server
- **cider-nrepl 0.30.0** - Integração com Cider/Emacs
- **tools.namespace 1.4.4** - Recarregamento de namespaces

#### **Formatação e Qualidade**

- **cljfmt 0.9.0** - Formatação automática de código

### Testes

#### **Framework de Testes**

- **clojure.test** - Framework de testes nativo
- **test.check 1.1.1** - Testes baseados em propriedades
- **matcher-combinators 3.8.8** - Matchers avançados para testes
- **ring-mock 0.4.0** - Mocking para testes de API

### Infraestrutura

- **PostgreSQL 15-alpine** - Banco de dados principal
- **Redis 7-alpine** - Cache e sessões
- **Docker + Compose** - Containerização completa
- **Node.js 18-alpine** - Runtime para frontend (≥18.0.0)
- **OpenJDK 17** - Runtime Java para backend

### Documentação

- **OpenAPI 2.0** - Especificação da API
- **Swagger UI** - Interface de documentação interativa

### APIs Externas

- **CoinGecko API** - Dados de criptomoedas (principal)
- **Binance API** - Dados de exchange (implementado)

## 📋 Etapas do Projeto

### ✅ Fase 1: Fundação

- ✅ **Setup do projeto** - Estrutura Clojure + Docker
- ✅ **Configuração PostgreSQL** - Banco de dados com migrações
- ✅ **Configuração Redis** - Cache e sessões

### ✅ Fase 2: Coleta de Dados

- ✅ **Cliente CoinGecko API** - Integração completa com rate limiting
- ✅ **Cliente Binance API** - Dados de exchange implementados
- ✅ **Sistema de coleta** - core.async com agendamento automático
- ✅ **Persistência** - Camada de dados com next.jdbc

### ✅ Fase 3: Backend

- ✅ **API REST** - 20 endpoints implementados com Swagger
- ✅ **Sistema de alertas** - 4 tipos de alertas inteligentes
- ✅ **WebSocket** - Atualizações em tempo real
- ✅ **Análises avançadas** - Correlações e estatísticas
- ✅ **Documentação OpenAPI** - Swagger UI interativo

### ✅ Fase 4: Frontend

- ✅ **Dashboard ClojureScript** - Interface moderna e responsiva
- ✅ **Tema claro/escuro** - Sistema de temas implementado
- ✅ **WebSocket cliente** - Conexão em tempo real
- ✅ **Componentes Reagent** - Interface reativa

### ✅ Fase 5: Qualidade

- ✅ **Testes completos** - 100% dos módulos principais testados
- ✅ **Documentação** - API, guias e especificações
- ✅ **Scripts de automação** - Makefile e scripts Docker
- ✅ **Deploy produção** - Docker Compose e CI/CD

## 🌐 API REST

A API oferece 20 endpoints com **documentação Swagger completa**:

### 📚 **Documentação Interativa**

- **Swagger UI**: http://localhost:3000/api-docs/
- **Especificação JSON**: http://localhost:3000/swagger.json
- **Health Check**: http://localhost:3000/api/health

### 🔗 **Principais Endpoints**

```bash
# Health & System
GET /api/health              # Verificação de saúde
GET /api/system/status       # Status do sistema
POST /api/system/collect     # Força coleta de dados

# Moedas
GET /api/coins               # Lista todas as moedas
GET /api/coins/:symbol       # Detalhes de uma moeda
GET /api/search/coins        # Busca moedas

# Preços
GET /api/prices/current      # Preços atuais
GET /api/prices/current/:symbol  # Preço atual de uma moeda
GET /api/prices/history/:symbol  # Histórico de preços

# Mercado
GET /api/market/overview     # Visão geral do mercado
GET /api/market/gainers      # Maiores altas
GET /api/market/losers       # Maiores baixas

# Alertas
GET /api/alerts              # Listar alertas
POST /api/alerts             # Criar alerta
GET /api/alerts/:alert-id    # Detalhes do alerta
PUT /api/alerts/:alert-id    # Atualizar alerta
DELETE /api/alerts/:alert-id # Excluir alerta

# Binance
GET /api/binance/ticker      # Ticker 24h
GET /api/binance/klines/:symbol  # Dados de candlestick
GET /api/binance/orderbook/:symbol  # Livro de ofertas

# Análises
GET /api/analytics/correlation  # Correlação entre moedas
POST /api/analytics/portfolio   # Performance de portfolio
GET /api/stats/:symbol          # Estatísticas detalhadas de uma moeda
```

> 💡 **Dica**: Use a interface Swagger para testar endpoints interativamente!

## 🚀 Acesse a aplicação em Produção

- **Frontend**: http://localhost:3000
- **API Health Check**: http://localhost:3000/api/health
- **📚 Documentação API (Swagger UI)**: http://localhost:3000/api-docs/
- **📋 Especificação OpenAPI (JSON)**: http://localhost:3000/swagger.json
- **WebSocket**: ws://localhost:3000/ws

## 📄 Licença

Este projeto está licenciado sob a Licença MIT - veja o arquivo [LICENSE](LICENSE) para detalhes.

## 🙏 Agradecimentos

- [CoinGecko](https://www.coingecko.com/) - API de dados de criptomoedas
- [Binance](https://www.binance.com/) - API de exchange
- Comunidade Clojure - Ferramentas e bibliotecas incríveis

---

<div align="center">

**⭐ Se este projeto foi útil, avalie com uma estrela! ⭐**

Desenvolvido por Bruno Guedes usando Clojure e ClojureScript.

</div>
