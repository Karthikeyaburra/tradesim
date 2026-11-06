# TradeSim — Real-Time Stock Trading Platform & ACID Financial Ledger

[![CI — MySQL Concurrency & Integration Suite](https://github.com/Karthikeyaburra/tradesim/actions/workflows/ci.yml/badge.svg)](https://github.com/Karthikeyaburra/tradesim/actions/workflows/ci.yml)

A full-stack virtual stock-trading web platform built with **Java 21, Spring Boot 4, MySQL 8 (JDBC), live NSE market quote anchoring, and a zero-dependency WCAG-accessible ES6 Web Client**. Designed around payment-grade transactional invariants, row-level concurrency control without JVM locks, and clean Object-Oriented architecture.

---

## Key Engineering Highlights

### 1. Concurrency Without JVM Locks (InnoDB Conditional SQL)
- **Atomic Cash Debit (`Market Buy`) / Escrow (`Limit Buy`):**
  ```sql
  UPDATE wallets SET balance = balance - ?, updated_at = ?
  WHERE user_id = ? AND balance >= ?
  ```
  MySQL acquires an exclusive row lock and evaluates `balance >= ?` against the latest committed value in one atomic statement—eliminating `SELECT`-then-`UPDATE` TOCTOU race conditions across multiple app instances.
- **Pending-Only Order State Machine (`Limit Fill` vs. `User Cancel`):**
  Both `OrderRepository.markFilledIfPending` and `markCancelledIfPending` gate on `WHERE id = ? AND status = 'PENDING'`. Even if a user clicks **Cancel** at the exact millisecond the price engine matches their limit order, InnoDB guarantees **exactly one transition wins (`FILLED` XOR `CANCELLED`)**—preventing double fills and double refunds.
- **Verified by Automated Concurrency Tests (`TradeSimConcurrencyAndFlowTests`):**
  - **50 concurrent threads** racing `MARKET BUY` orders of ₹1,00,000 against a ₹10,00,000 balance -> **10 filled, 40 rejected (`409`), ₹0.00 final balance, zero deadlocks**.
  - **20 concurrent threads** racing `Cancel` vs. `Limit Match` on a resting order -> **1 winner, ₹0.00 orphaned escrow**.

### 2. Append-Only Financial Ledger Invariant
Every trader wallet maintains a continuous double-check accounting invariant across all operations (signup, market/limit fills, price-improvement refunds, admin deposits, delisting cash settlements, and account resets):
$$\text{Available Cash} + \text{Reserved Limit Cash} \equiv \sum \text{ledger\_entries.amount}$$
Database `CHECK` constraints (`balance >= 0`, `reserved_cash >= 0`, `reserved_quantity <= quantity`) enforce invariants at the storage boundary as a second line of defence.

### 3. Object-Oriented Architecture
- **Strategy Pattern (`OrderExecutionStrategy`):** `OrderService` dispatches polymorphically to `MarketOrderStrategy` and `LimitOrderStrategy` via an `EnumMap<OrderType, OrderExecutionStrategy>`, adhering to the Open/Closed Principle.
- **Repository Pattern:** Services depend exclusively on repository interfaces (`WalletRepository`, `OrderRepository`, `HoldingRepository`, `LedgerRepository`, `StockRepository`, `UserRepository`, `SessionRepository`), decoupling domain rules from SQL `JdbcTemplate` persistence.
- **Immutable Value Objects & DTO Isolation:** Java `record` types (`Money`, `Wallet`, `Order`, `Position`, `LedgerEntry`) enforce exact base-10 `BigDecimal` arithmetic (`RoundingMode.HALF_UP`), while response DTOs (`UserResponse`, `PortfolioResponse`) prevent internal fields like BCrypt `passwordHash` from ever serializing over HTTP.
- **Exception Hierarchy (`@RestControllerAdvice`):** Polymorphic `NotFoundException` (`404`) and `ConflictException` (`409`) hierarchies map cleanly to structured `ApiError` responses.

### 4. Real-Time SSE Trading Workstation & Accessible Web UI (WCAG 2.1 AA)
- **Push Market Feed (`SseEmitter`):** [`MarketStreamService`](src/main/java/com/tradesim/tradesim/service/MarketStreamService.java) broadcasts 1.5s server ticks over `/api/stream/market` (`text/event-stream`) with automatic reconnect fallback—zero polling jitter.
- **High-DPI `<canvas>` Charting & Depth Ladder:** Custom `devicePixelRatio`-scaled OHLC Candlestick + Area + `SMA(8)` chart with hover crosshair, live resting-limit-order price overlays, inline watchlist sparklines, and a click-to-price Bid/Ask market depth ladder.
- **WCAG 2.1 AA & Institutional Dark/Light Themes:** Obsidian trading terminal theme with 1-click high-contrast Light mode, `font-variant-numeric: tabular-nums` so ticking numerals never shift layout, semantic HTML5 landmarks, skip-to-content link, `role="tablist"`, `aria-live="polite"` notifications, directional `▲`/`▼` glyphs for colorblind safety, and full keyboard operation (`/` search, `B`/`S` side toggle, `Enter` select, `Esc` modal).

---

## Features
- **Hybrid Live NSE Market Sync (`MarketDataProvider`):** [`YahooFinanceNseClient`](src/main/java/com/tradesim/tradesim/service/market/YahooFinanceNseClient.java) and [`LiveMarketSyncService`](src/main/java/com/tradesim/tradesim/service/market/LiveMarketSyncService.java) anchor prices and 5-minute intraday histories to live NSE India market data (`RELIANCE.NS`, `TCS.NS`, etc.) every 60s, while [`PriceEngine`](src/main/java/com/tradesim/tradesim/service/PriceEngine.java) interpolates mean-reverting 1.5s SSE micro-ticks 24/7 (including outside NSE market hours).
- **Auth & RBAC:** BCrypt salted password hashing, 256-bit `SecureRandom` Bearer sessions with TTL expiry, `TRADER` (₹10,00,000 opening capital + 1-click Instant Trader Demo) and `ADMIN` roles.
- **Live NSE Market & Multi-Mode Charting:** 16 seeded Nifty-50 equities across 9 sectors, scheduled `PriceEngine` (bounded Gaussian random walk with ±20% circuit bands), real-time SSE ticker tape + watchlist flash animations, and OHLC Candlestick / Area charting.
- **Pro Order Ticket & Docked Blotter:** Market & Limit orders with `25% / 50% / 75% / MAX` position sizers, `-1% / Mid / +1%` limit snap buttons, cash/share escrow reservation, price-improvement refunds, 1-click position close, and atomic pending order cancellation.
- **Portfolio, Ledger Proof & Leaderboard:** Real-time weighted-average buy price, visual asset allocation bar, live double-entry ledger invariant equation proof, and a relational `JOIN + GROUP BY` trader leaderboard podium.
- **Filterable Audit & CSV Export:** Multi-criteria order filtering (symbol, side, type, status, date range) with streaming CSV download (`/api/orders/export.csv`).
- **Exchange Admin Console:** Add/relist equities, delist equities with automatic pending-order cancellation & cash settlement of shareholder positions, inspect user ledger consistency, deposit funds, and reset accounts.

---

## Running Locally

### Option A: Local Java + MySQL 8
1. Ensure MySQL 8 is running on `localhost:3306`.
2. Create `local.properties` in the project root (git-ignored):
   ```properties
   DB_PASSWORD=your_mysql_password
   ```
3. Start the application:
   ```bash
   ./mvnw spring-boot:run
   ```
4. Open **http://localhost:8080**
   - **Trader:** Click **Create Trader Account** (starts with ₹10,00,000.00).
   - **Admin:** Sign in with `admin` / `admin123`.

### Option B: 1-Command Docker Compose (Production-Ready)
```bash
docker compose up --build
```
Opens on **http://localhost:8080** with an isolated MySQL 8 container and healthchecks.

### Deploying to Cloud (Railway / Render / AWS)
Deploy using the root `Dockerfile` and set environment variables:
`PORT`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `ADMIN_PASSWORD`.
Readiness probe endpoint: `GET /api/health`.

---

## Running Automated Tests
```bash
./mvnw test
```
Runs context load, the **50-thread concurrent market-buy stress test**, the **20-thread cancel-vs-match race test**, and the end-to-end trading/ledger lifecycle test against MySQL.

