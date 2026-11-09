# TradeSim — Real-Time NSE Stock Trading Platform

[![CI — MySQL Concurrency & Integration Suite](https://github.com/Karthikeyaburra/tradesim/actions/workflows/ci.yml/badge.svg)](https://github.com/Karthikeyaburra/tradesim/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.2-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL 8](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?logo=docker&logoColor=white)](https://www.docker.com/)

A production-grade, real-time multi-user virtual stock trading platform built with **Java 21, Spring Boot 3, MySQL 8 (InnoDB), Server-Sent Events, and Docker**. Designed around payment-grade transactional invariants, row-level concurrency control without JVM locks, and clean object-oriented architecture.

---

## 🎯 Why This Project

Most student trading apps use in-memory state or naive `SELECT`-then-`UPDATE` patterns that race under load. TradeSim demonstrates **how to build a financially correct backend**:

- **Zero JVM locks** — all concurrency handled by InnoDB row-level conditional updates
- **Append-only ledger invariant** — `Available Cash + Reserved Cash ≡ Σ Ledger Entries` enforced by DB `CHECK` constraints
- **Real-time market feed** — SSE push at 1.5s intervals with automatic reconnect (no polling)
- **WCAG 2.1 AA accessible frontend** — high-contrast themes, tabular numerals, full keyboard nav

---

## ⚡ Key Engineering Highlights

### 1. Concurrency Without JVM Locks (InnoDB Conditional SQL)

| Operation | Atomic SQL Guard | Race Prevented |
|-----------|------------------|----------------|
| **Market Buy** | `UPDATE wallets SET balance = balance - ? WHERE user_id = ? AND balance >= ?` | Overdraft across concurrent threads |
| **Limit Buy (Escrow)** | `UPDATE wallets SET reserved_cash = reserved_cash + ? WHERE user_id = ? AND balance >= ?` | Double-reserve on rapid clicks |
| **Limit Fill** | `UPDATE orders SET status = 'FILLED' WHERE id = ? AND status = 'PENDING'` | Fill vs. Cancel TOCTOU |
| **User Cancel** | `UPDATE orders SET status = 'CANCELLED' WHERE id = ? AND status = 'PENDING'` | Cancel vs. Fill XOR guarantee |

**Verified by automated concurrency tests** (`TradeSimConcurrencyAndFlowTests`):
- 50 concurrent threads × ₹1,00,000 market buys against ₹10,00,000 balance → **10 filled, 40 rejected (409), ₹0.00 final balance, zero deadlocks**
- 20 concurrent threads racing Cancel vs. Limit Match → **1 winner, ₹0.00 orphaned escrow**

### 2. Append-Only Financial Ledger Invariant

Every wallet maintains a continuous double-entry accounting invariant across **all** operations (signup, market/limit fills, price-improvement refunds, admin deposits, delisting settlements, account resets):

```
Available Cash + Reserved Limit Cash ≡ Σ ledger_entries.amount
```

Database `CHECK` constraints (`balance >= 0`, `reserved_cash >= 0`, `reserved_quantity <= quantity`) enforce invariants at the storage boundary as a second line of defence.

### 3. Object-Oriented Architecture

| Pattern | Implementation |
|---------|----------------|
| **Strategy** | `OrderExecutionStrategy` interface → `MarketOrderStrategy` / `LimitOrderStrategy` dispatched via `EnumMap<OrderType, OrderExecutionStrategy>` (Open/Closed Principle) |
| **Repository** | Services depend on interfaces (`WalletRepository`, `OrderRepository`, `HoldingRepository`, `LedgerRepository`, `StockRepository`, `UserRepository`, `SessionRepository`) — decoupled from `JdbcTemplate` persistence |
| **Value Objects** | Java `record` types (`Money`, `Wallet`, `Order`, `Position`, `LedgerEntry`) enforce exact base-10 `BigDecimal` arithmetic (`RoundingMode.HALF_UP`) |
| **DTO Isolation** | Response DTOs (`UserResponse`, `PortfolioResponse`) prevent internal fields (e.g., BCrypt `passwordHash`) from ever serializing over HTTP |
| **Exception Hierarchy** | `@RestControllerAdvice` maps polymorphic `NotFoundException` (404) and `ConflictException` (409) to structured `ApiError` responses |

### 4. Real-Time SSE Trading Workstation (WCAG 2.1 AA)

- **Push Market Feed** — `MarketStreamService` broadcasts 1.5s server ticks over `/api/stream/market` (`text/event-stream`) with automatic reconnect fallback
- **High-DPI `<canvas>` Charting** — OHLC Candlestick + Area + `SMA(8)` with hover crosshair, live resting-limit-order overlays, inline watchlist sparklines, click-to-price Bid/Ask depth ladder
- **Accessibility** — Obsidian trading terminal theme + 1-click high-contrast Light mode, `font-variant-numeric: tabular-nums` (ticking numerals never shift), semantic HTML5 landmarks, skip-to-content, `role="tablist"`, `aria-live="polite"`, directional `▲`/`▼` glyphs for colorblind safety, full keyboard operation (`/` search, `B`/`S` side toggle, `Enter` select, `Esc` modal)

---

## 🧩 Features

| Area | Capability |
|------|------------|
| **Market Data** | Hybrid live NSE sync: Yahoo Finance anchor (60s) + PriceEngine mean-reverting Gaussian micro-ticks (1.5s SSE) with ±20% circuit bands; 16 seeded Nifty-50 equities across 9 sectors |
| **Auth & RBAC** | BCrypt salted hashing, 256-bit `SecureRandom` Bearer sessions with TTL, `TRADER` (₹10,00,000 opening capital + 1-click Instant Demo) and `ADMIN` roles |
| **Order Ticket** | Market & Limit orders with 25%/50%/75%/MAX position sizers, -1%/Mid/+1% limit snap buttons, cash/share escrow, price-improvement refunds, 1-click position close, atomic pending cancellation |
| **Portfolio** | Real-time weighted-average buy price, visual asset allocation bar, live double-entry ledger invariant proof |
| **Leaderboard** | Relational `JOIN + GROUP BY` trader podium |
| **Audit & Export** | Multi-criteria order filtering (symbol, side, type, status, date range) + streaming CSV download (`/api/orders/export.csv`) |
| **Admin Console** | Add/relist equities, delist with auto pending-order cancellation & cash settlement, inspect user ledger consistency, deposit funds, reset accounts |

---

## 🏗️ Repository Structure

```
tradesim/
├── .github/workflows/ci.yml          # Concurrency + integration test suite
├── docs/                             # Architecture diagrams, API reference
├── src/
│   ├── main/java/com/tradesim/
│   │   ├── config/                   # Security, SSE, Jackson, WebMvc
│   │   ├── controller/               # REST endpoints (auth, market, orders, portfolio, admin)
│   │   ├── dto/                      # Request/response DTOs (no domain leakage)
│   │   ├── exception/                # ApiError, NotFoundException, ConflictException
│   │   ├── model/                    # JPA entities + record value objects (Money, Wallet, Order, Position, LedgerEntry)
│   │   ├── repository/               # JdbcTemplate implementations of repository interfaces
│   │   ├── service/                  # Domain services (MarketStream, PriceEngine, OrderExecutionStrategy, Wallet, Ledger)
│   │   └── TradeSimApplication.java
│   └── test/                         # Concurrency stress tests, end-to-end lifecycle tests
├── docker-compose.yml                # Production-ready MySQL 8 + app with healthchecks
├── Dockerfile                        # Multi-stage build (Maven → JRE)
├── pom.xml                           # Maven: Spring Boot 3, MySQL, JUnit 5, Mockito, Testcontainers
├── railway.json                      # Railway deploy config
└── mvnw / mvnw.cmd                   # Maven wrapper
```

---

## 🚀 Quick Start

### Option A: Docker Compose (Recommended — 1 command)
```bash
docker compose up --build
```
Opens **http://localhost:8080** with isolated MySQL 8 container and healthchecks.

### Option B: Local Java + MySQL 8
```bash
# 1. Start MySQL 8 on localhost:3306
# 2. Create local.properties (git-ignored):
cat > local.properties << 'EOF'
DB_PASSWORD=your_mysql_password
EOF

# 3. Run
./mvnw spring-boot:run
```
Open **http://localhost:8080**
- **Trader:** Click **Create Trader Account** (starts with ₹10,00,000.00)
- **Admin:** Sign in with `admin` / `admin123`

### Cloud Deploy (Railway / Render / AWS)
Deploy using the root `Dockerfile` and set env vars:
`PORT`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `ADMIN_PASSWORD`  
Readiness probe: `GET /api/health`

---

## 🧪 Running Tests

```bash
./mvnw test
```

Runs:
- Context load test
- **50-thread concurrent market-buy stress test** (verifies zero overdraft, zero deadlock)
- **20-thread cancel-vs-match race test** (verifies XOR transition)
- End-to-end trading/ledger lifecycle test against MySQL

---

## 📚 Design Decisions (For Interview Discussion)

| Decision | Rationale |
|----------|-----------|
| **JdbcTemplate over JPA/Hibernate** | Explicit SQL = predictable locking behavior; conditional `UPDATE ... WHERE` maps directly to InnoDB row locks; no hidden N+1 or cascade surprises |
| **Conditional UPDATE for concurrency** | Single atomic statement replaces `SELECT`-then-`UPDATE` TOCTOU; InnoDB evaluates predicate against latest committed version under row lock |
| **`record` value objects + `BigDecimal`** | Immutable, no setter leakage; `RoundingMode.HALF_UP` matches financial rounding standards; `Money` encapsulates currency logic |
| **SSE over WebSocket** | Simpler (HTTP/1.1 compatible), auto-reconnect via `EventSource`, unidirectional market feed fits SSE perfectly |
| **WCAG 2.1 AA from day one** | Not an afterthought — tabular nums, colorblind glyphs, ARIA live regions, keyboard-first nav built into charting components |

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.