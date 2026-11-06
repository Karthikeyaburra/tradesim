-- TradeSim schema (MySQL 8). Idempotent: safe to run on every startup.
-- Correctness rules live here too: CHECK constraints are the last line of defence
-- behind the conditional UPDATEs in the repositories.

CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT chk_users_role CHECK (role IN ('TRADER', 'ADMIN'))
);

CREATE TABLE IF NOT EXISTS sessions (
    token      VARCHAR(64)  PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_sessions_expires (expires_at)
);

CREATE TABLE IF NOT EXISTS stocks (
    symbol         VARCHAR(16)    PRIMARY KEY,
    name           VARCHAR(100)   NOT NULL,
    price          DECIMAL(18, 2) NOT NULL,
    previous_close DECIMAL(18, 2) NOT NULL,
    sector         VARCHAR(50)    NOT NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_stocks_price CHECK (price > 0),
    CONSTRAINT chk_stocks_prev_close CHECK (previous_close > 0)
);

CREATE TABLE IF NOT EXISTS price_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    symbol      VARCHAR(16)    NOT NULL,
    price       DECIMAL(18, 2) NOT NULL,
    recorded_at TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_price_history_stock FOREIGN KEY (symbol) REFERENCES stocks (symbol) ON DELETE CASCADE,
    INDEX idx_price_history_symbol_time (symbol, recorded_at)
);

-- Cash invariant: balance + reserved_cash == SUM(ledger_entries.amount) for the user.
CREATE TABLE IF NOT EXISTS wallets (
    user_id       BIGINT         PRIMARY KEY,
    balance       DECIMAL(18, 2) NOT NULL,
    reserved_cash DECIMAL(18, 2) NOT NULL DEFAULT 0.00,
    net_deposits  DECIMAL(18, 2) NOT NULL,
    updated_at    TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_wallets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_wallets_balance CHECK (balance >= 0),
    CONSTRAINT chk_wallets_reserved CHECK (reserved_cash >= 0)
);

-- quantity = total shares owned; reserved_quantity = shares locked by pending SELL limit orders.
CREATE TABLE IF NOT EXISTS holdings (
    user_id           BIGINT         NOT NULL,
    symbol            VARCHAR(16)    NOT NULL,
    quantity          INT            NOT NULL,
    reserved_quantity INT            NOT NULL DEFAULT 0,
    avg_buy_price     DECIMAL(18, 2) NOT NULL,
    PRIMARY KEY (user_id, symbol),
    CONSTRAINT fk_holdings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_holdings_stock FOREIGN KEY (symbol) REFERENCES stocks (symbol),
    CONSTRAINT chk_holdings_qty CHECK (quantity >= 0),
    CONSTRAINT chk_holdings_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= quantity)
);

CREATE TABLE IF NOT EXISTS orders (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT         NOT NULL,
    symbol         VARCHAR(16)    NOT NULL,
    side           VARCHAR(8)     NOT NULL,
    type           VARCHAR(8)     NOT NULL,
    quantity       INT            NOT NULL,
    limit_price    DECIMAL(18, 2) NULL,
    executed_price DECIMAL(18, 2) NULL,
    status         VARCHAR(16)    NOT NULL,
    created_at     TIMESTAMP(6)   NOT NULL,
    updated_at     TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_orders_stock FOREIGN KEY (symbol) REFERENCES stocks (symbol),
    CONSTRAINT chk_orders_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_orders_type CHECK (type IN ('MARKET', 'LIMIT')),
    CONSTRAINT chk_orders_qty CHECK (quantity > 0),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'FILLED', 'CANCELLED')),
    CONSTRAINT chk_orders_limit CHECK ((type = 'LIMIT' AND limit_price > 0) OR (type = 'MARKET' AND limit_price IS NULL)),
    INDEX idx_orders_user_created (user_id, created_at),
    INDEX idx_orders_status_symbol (status, symbol)
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id            BIGINT         NOT NULL,
    type               VARCHAR(24)    NOT NULL,
    amount             DECIMAL(18, 2) NOT NULL,
    balance_after      DECIMAL(18, 2) NOT NULL,
    reference_order_id BIGINT         NULL,
    description        VARCHAR(255)   NOT NULL,
    created_at         TIMESTAMP(6)   NOT NULL,
    CONSTRAINT fk_ledger_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_order FOREIGN KEY (reference_order_id) REFERENCES orders (id) ON DELETE SET NULL,
    INDEX idx_ledger_user_created (user_id, created_at)
);
