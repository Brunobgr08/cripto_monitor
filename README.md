# 📈 Cripto Monitor

> Sistema completo de monitoramento de criptomoedas em tempo real desenvolvido em Clojure/ClojureScript

[![Clojure](https://img.shields.io/badge/Clojure-1.11.1-blue.svg)](https://clojure.org/)
[![ClojureScript](https://img.shields.io/badge/ClojureScript-1.11.60-blue.svg)](https://clojurescript.org/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-green.svg)](#-testes)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## 🚀 Visão Geral

O **Cripto Monitor** é uma plataforma completa para monitoramento, análise e alertas de criptomoedas em tempo real. Desenvolvido com tecnologias modernas e arquitetura robusta, oferece desde funcionalidades básicas para iniciantes até ferramentas avançadas para traders profissionais.

### ✨ Principais Funcionalidades

- 📊 **Monitoramento em Tempo Real** - Preços atualizados via WebSocket com 5 moedas principais
- 🚨 **Sistema de Alertas Inteligentes** - 4 tipos de alertas (preço, variação %, volume, correlação)
- 📈 **Dashboard Interativo** - Interface moderna com tema claro/escuro
- 🔌 **API REST Completa** - 25+ endpoints para todas as funcionalidades
- 🎨 **Interface Web Responsiva** - ClojureScript + Reagent + Re-frame
- 🐳 **Deploy Simplificado** - Containerização completa com Docker
- 🧪 **Cobertura de Testes** - 100% dos módulos principais testados

## 🛠️ Tecnologias

### Backend

- **Clojure 1.11.1** - Linguagem principal
- **Ring 1.10.0 + Reitit 0.7.0** - Framework web e roteamento
- **http-kit 2.7.0** - Servidor HTTP e WebSocket
- **next.jdbc 1.3.909 + HikariCP 3.0.1** - Acesso ao banco de dados com pool de conexões
- **core.async 1.6.681** - Programação assíncrona e canais
- **Timbre 6.3.1** - Sistema de logs estruturados
- **Cheshire 5.12.0** - Serialização JSON
- **Aero 1.1.6** - Gerenciamento de configurações

### Frontend

- **ClojureScript 1.12.42** - Clojure para JavaScript (via Shadow-cljs)
- **Reagent 1.2.0** - Componentes React funcionais
- **Re-frame 1.3.0** - Gerenciamento de estado (padrão Flux)
- **Shadow-cljs 2.25.10** - Build tool e hot-reload
- **React 18.2.0** - Biblioteca de componentes
- **Recharts 2.8.0** - Gráficos e visualizações interativas

### Infraestrutura

- **PostgreSQL 15-alpine** - Banco de dados principal
- **Redis 7-alpine** - Cache e sessões
- **Docker + Compose** - Containerização completa
- **Node.js 18-alpine** - Runtime para frontend
- **OpenJDK 17** - Runtime Java para backend

### APIs Externas

- **CoinGecko API** - Dados de criptomoedas (principal)
- **Binance API** - Dados de exchange (implementado)

### Testes

- **clojure.test** - Framework de testes nativo
- **test.check** - Testes baseados em propriedades
- **matcher-combinators** - Matchers avançados para testes
- **ring/ring-mock** - Mocking para testes de API

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

- ✅ **API REST** - 25+ endpoints implementados
- ✅ **Sistema de alertas** - 4 tipos de alertas inteligentes
- ✅ **WebSocket** - Atualizações em tempo real
- ✅ **Análises avançadas** - Correlações e estatísticas

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
