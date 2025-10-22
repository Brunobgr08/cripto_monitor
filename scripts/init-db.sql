-- Initial database setup for Cripto Monitor
-- This script will be executed when PostgreSQL container starts

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create coins table
CREATE TABLE IF NOT EXISTS coins (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    coingecko_id VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create price_history table
CREATE TABLE IF NOT EXISTS price_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    coin_id UUID REFERENCES coins(id) ON DELETE CASCADE,
    price_usd DECIMAL(20,8) NOT NULL,
    market_cap BIGINT,
    volume_24h BIGINT,
    change_24h_percent DECIMAL(10,4),
    collected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(50) NOT NULL DEFAULT 'coingecko',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create alerts table
CREATE TABLE IF NOT EXISTS alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    coin_id UUID REFERENCES coins(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,
    threshold_value DECIMAL(20,8) NOT NULL,
    condition VARCHAR(10) NOT NULL CHECK (condition IN ('>', '<', '>=', '<=')),
    is_active BOOLEAN DEFAULT true,
    notification_method VARCHAR(50) NOT NULL DEFAULT 'webhook',
    webhook_url TEXT,
    email TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    last_triggered TIMESTAMP WITH TIME ZONE
);

-- Create alert_history table
CREATE TABLE IF NOT EXISTS alert_history (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id UUID REFERENCES alerts(id) ON DELETE CASCADE,
    triggered_price DECIMAL(20,8) NOT NULL,
    triggered_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    notification_sent BOOLEAN DEFAULT false,
    notification_response TEXT
);

-- Create user_preferences table (for future use)
CREATE TABLE IF NOT EXISTS user_preferences (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(100) NOT NULL,
    watched_coins JSONB DEFAULT '[]'::jsonb,
    alert_settings JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_price_history_coin_time ON price_history(coin_id, collected_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_history_collected_at ON price_history(collected_at DESC);
CREATE INDEX IF NOT EXISTS idx_price_history_symbol_time ON price_history(coin_id, collected_at DESC) 
    INCLUDE (price_usd, market_cap, volume_24h);

CREATE INDEX IF NOT EXISTS idx_alerts_active ON alerts(is_active) WHERE is_active = true;
CREATE INDEX IF NOT EXISTS idx_alerts_coin_active ON alerts(coin_id, is_active) WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_alert_history_triggered_at ON alert_history(triggered_at DESC);

-- Insert initial coin data
INSERT INTO coins (symbol, name, coingecko_id) VALUES 
    ('BTC', 'Bitcoin', 'bitcoin'),
    ('ETH', 'Ethereum', 'ethereum'),
    ('SOL', 'Solana', 'solana'),
    ('ADA', 'Cardano', 'cardano'),
    ('DOT', 'Polkadot', 'polkadot')
ON CONFLICT (symbol) DO NOTHING;

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create triggers for updated_at
CREATE TRIGGER update_coins_updated_at BEFORE UPDATE ON coins
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_preferences_updated_at BEFORE UPDATE ON user_preferences
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Grant permissions (for development)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO dev_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO dev_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO dev_user;

-- Create a view for latest prices
CREATE OR REPLACE VIEW latest_prices AS
SELECT DISTINCT ON (c.symbol)
    c.symbol,
    c.name,
    ph.price_usd,
    ph.market_cap,
    ph.volume_24h,
    ph.change_24h_percent,
    ph.collected_at,
    ph.source
FROM coins c
LEFT JOIN price_history ph ON c.id = ph.coin_id
ORDER BY c.symbol, ph.collected_at DESC;

-- Create a view for price statistics
CREATE OR REPLACE VIEW price_statistics AS
SELECT 
    c.symbol,
    c.name,
    COUNT(ph.id) as total_records,
    MIN(ph.price_usd) as min_price,
    MAX(ph.price_usd) as max_price,
    AVG(ph.price_usd) as avg_price,
    STDDEV(ph.price_usd) as price_volatility,
    MIN(ph.collected_at) as first_record,
    MAX(ph.collected_at) as last_record
FROM coins c
LEFT JOIN price_history ph ON c.id = ph.coin_id
GROUP BY c.id, c.symbol, c.name;

COMMIT;
