# 📚 Documentação da API - OpenAPI/Swagger

## 🎯 **Visão Geral**

O Cripto Monitor agora possui documentação automática da API usando **OpenAPI 3.0** (Swagger), proporcionando:

- ✅ **Documentação interativa** - Teste endpoints diretamente no navegador
- ✅ **Especificações completas** - Schemas, parâmetros, respostas e exemplos
- ✅ **Validação automática** - Verificação de tipos e formatos
- ✅ **Geração de código** - Clientes podem ser gerados automaticamente

---

## 🚀 **Como Acessar**

- **📚 Interface Swagger UI**: http://localhost:3000/api-docs/
- **📄 Especificação JSON**: http://localhost:3000/swagger.json
- **❤️ Health Check**: http://localhost:3000/api/health

---

## 🎨 **Interface Swagger UI**

A interface Swagger UI oferece:

### **📋 Funcionalidades Principais**

- **Exploração visual** de todos os endpoints
- **Teste interativo** - Execute requests diretamente
- **Documentação detalhada** - Descrições, parâmetros e exemplos
- **Schemas de dados** - Estruturas de request/response
- **Códigos de resposta** - Todos os status HTTP possíveis

### **🔧 Como Usar**

1. **Navegue pelos endpoints** organizados por tags
2. **Clique em "Try it out"** para testar um endpoint
3. **Preencha os parâmetros** necessários
4. **Execute** e veja a resposta em tempo real
5. **Copie o comando curl** gerado automaticamente

---

## 📊 **Organização da API**

### **🏷️ Tags (Categorias)**

| Tag           | Descrição                | Endpoints          |
| ------------- | ------------------------ | ------------------ |
| **health**    | Verificação de saúde     | `/api/health`      |
| **coins**     | Operações com moedas     | `/api/coins/*`     |
| **prices**    | Operações com preços     | `/api/prices/*`    |
| **market**    | Dados de mercado         | `/api/market/*`    |
| **search**    | Busca e pesquisa         | `/api/search/*`    |
| **system**    | Operações do sistema     | `/api/system/*`    |
| **binance**   | Integração com Binance   | `/api/binance/*`   |
| **analytics** | Análises e estatísticas  | `/api/analytics/*` |
| **alerts**    | Gerenciamento de alertas | `/api/alerts/*`    |

### **📈 Endpoints Principais**

#### **🔍 Health & System**

- `GET /api/health` - Verificação de saúde
- `GET /api/system/status` - Status completo do sistema
- `POST /api/system/collect` - Força coleta de dados

#### **🪙 Moedas**

- `GET /api/coins` - Lista todas as moedas
- `GET /api/coins/{symbol}` - Detalhes de uma moeda
- `GET /api/search/coins?q={termo}` - Busca moedas

#### **💰 Preços**

- `GET /api/prices/current` - Preços atuais
- `GET /api/prices/current/{symbol}` - Preço atual de uma moeda
- `GET /api/prices/history/{symbol}` - Histórico de preços

#### **📊 Mercado**

- `GET /api/market/overview` - Visão geral do mercado
- `GET /api/market/gainers` - Maiores altas
- `GET /api/market/losers` - Maiores baixas

#### **📈 Análises**

- `GET /api/analytics/correlation` - Correlação entre moedas
- `POST /api/analytics/portfolio` - Performance de portfolio
- `GET /api/stats/{symbol}` - Estatísticas detalhadas de uma moeda

#### **🔔 Alertas**

- `GET /api/alerts` - Lista todos os alertas
- `POST /api/alerts` - Cria um novo alerta
- `GET /api/alerts/{alert-id}` - Detalhes de um alerta
- `PUT /api/alerts/{alert-id}` - Atualiza um alerta
- `DELETE /api/alerts/{alert-id}` - Remove um alerta

#### **🔗 Binance Integration**

- `GET /api/binance/ticker` - Ticker 24h
- `GET /api/binance/klines/{symbol}` - Dados de candlestick
- `GET /api/binance/orderbook/{symbol}` - Livro de ofertas

---

## 🔧 **Especificações Técnicas**

### **📋 Formato de Resposta Padrão**

**Sucesso (2xx):**

```json
{
  "success": true,
  "data": {
    /* dados da resposta */
  },
  "timestamp": "2024-11-11T15:30:00Z"
}
```

**Erro (4xx/5xx):**

```json
{
  "success": false,
  "error": "error_code",
  "message": "Descrição do erro",
  "timestamp": "2024-11-11T15:30:00Z"
}
```

### **🎯 Parâmetros Comuns**

| Parâmetro | Tipo    | Descrição                     | Exemplo      |
| --------- | ------- | ----------------------------- | ------------ |
| `symbol`  | string  | Símbolo da moeda              | `BTC`, `ETH` |
| `days`    | integer | Dias de histórico (1-365)     | `30`         |
| `limit`   | integer | Limite de resultados (1-1000) | `50`         |
| `q`       | string  | Termo de busca                | `bitcoin`    |

### **📊 Schemas de Dados**

#### **Moeda (Coin)**

```json
{
  "id": 1,
  "symbol": "BTC",
  "name": "Bitcoin",
  "coingecko_id": "bitcoin"
}
```

#### **Preço (Price)**

```json
{
  "symbol": "BTC",
  "price_usd": 45000.5,
  "market_cap": 850000000000,
  "volume_24h": 25000000000,
  "change_24h_percent": 2.5,
  "collected_at": "2024-11-11T15:30:00Z"
}
```

---

## 🎉 **Conclusão**

A documentação Swagger torna a API do Cripto Monitor:

- **Mais fácil de usar** para desenvolvedores
- **Autodocumentada** e sempre atualizada
- **Testável** diretamente no navegador
- **Profissional** e padronizada

**🔗 Acesse agora**: http://localhost:3000/api-docs/
