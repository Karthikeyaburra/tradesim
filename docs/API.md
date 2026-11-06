# TradeSim REST API Contract (v1)

Base URL: `http://localhost:8080`. JSON everywhere. Money = JSON number with 2 decimals (BigDecimal). Timestamps = ISO-8601 UTC strings (e.g. `"2026-11-05T10:11:39.964Z"`).
Auth: `Authorization: Bearer <token>` on every endpoint except those marked PUBLIC.

## Errors (all endpoints)
Non-2xx responses return:
```json
{ "status": 409, "message": "Insufficient funds: need 4120.75, available 100.00", "timestamp": "..." }
```
400 validation / bad input, 401 not logged in / bad token, 403 wrong role, 404 not found, 409 business conflict (insufficient funds/shares, order not pending, duplicate username/symbol).

## Enums
- Role: `TRADER`, `ADMIN`
- OrderSide: `BUY`, `SELL`
- OrderType: `MARKET`, `LIMIT`
- OrderStatus: `PENDING`, `FILLED`, `CANCELLED` (orders that fail validation/funds checks are rejected with 409 and never stored)
- LedgerType: `INITIAL_CREDIT`, `ADMIN_DEPOSIT`, `TRADE_BUY`, `TRADE_SELL`, `ACCOUNT_RESET`, `DELISTING_SETTLEMENT`

## Auth
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/api/auth/register` PUBLIC | `{"username","password"}` (username 3-30 `[A-Za-z0-9_]`, password 6-72) | 201 `{"token","expiresAt","user":UserResponse}` |
| POST | `/api/auth/login` PUBLIC | `{"username","password"}` | 200 `{"token","expiresAt","user":UserResponse}` |
| POST | `/api/auth/logout` | – | 204 |
| GET | `/api/auth/me` | – | 200 `UserResponse` |

`UserResponse`: `{"id":2,"username":"karthik","role":"TRADER","createdAt":"..."}`

## Market data (PUBLIC)
| GET | `/api/stocks` | 200 `StockResponse[]` sorted by symbol |
|---|---|---|
| GET | `/api/stocks/{symbol}` | 200 `StockResponse` |
| GET | `/api/stocks/{symbol}/history?limit=120` | 200 `PricePoint[]` oldest→newest (limit 1..1000, default 120) |
| GET | `/api/stream/market` | 200 `text/event-stream` SSE (`event:quotes`, pushes `StockResponse[]` every 1.5 s) |

`StockResponse`: `{"symbol":"TCS","name":"Tata Consultancy Services","sector":"IT","price":4120.75,"previousClose":4150.00,"change":-29.25,"changePercent":-0.70}`
`PricePoint`: `{"price":4120.75,"recordedAt":"..."}`
Server ticks prices every 1.5 s (configurable) and broadcasts live updates via SSE (`/api/stream/market`).

## Wallet (TRADER only)
GET `/api/wallet` → 200
```json
{ "balance": 990000.00, "reservedCash": 10000.00, "totalCash": 1000000.00, "ledgerConsistent": true,
  "updatedAt": "...", "ledger": [LedgerEntryResponse...newest first] }
```
`LedgerEntryResponse`: `{"id","type","amount","balanceAfter","referenceOrderId"(nullable),"description","createdAt"}` (amount negative for debits).

## Orders (TRADER only)
| Method | Path | Notes |
|---|---|---|
| POST | `/api/orders` | body `{"symbol":"TCS","side":"BUY","type":"MARKET","quantity":5,"limitPrice":null}`; `limitPrice` required (>0) for LIMIT, must be null/omitted for MARKET; quantity 1..100000. Returns 201 `OrderResponse` (MARKET → `FILLED`, LIMIT → `PENDING`, or `FILLED` immediately if marketable at placement). |
| GET | `/api/orders` | optional query params: `symbol`, `side`, `type`, `status`, `from` (yyyy-MM-dd, inclusive), `to` (yyyy-MM-dd, inclusive). Newest first. → `OrderResponse[]` |
| POST | `/api/orders/{id}/cancel` | 200 `OrderResponse` (status `CANCELLED`); 409 if not PENDING; 404 if not yours |
| GET | `/api/orders/export.csv` | same filters as list; `text/csv` attachment `tradesim-orders.csv`. Header: `order_id,created_at,symbol,side,type,quantity,limit_price,executed_price,total_value,status` |

`OrderResponse`: `{"id":12,"symbol":"TCS","side":"BUY","type":"LIMIT","quantity":5,"limitPrice":4000.00,"executedPrice":null,"totalValue":null,"status":"PENDING","createdAt":"...","updatedAt":"..."}` (`totalValue` = executedPrice × quantity when FILLED).

## Portfolio (TRADER only)
GET `/api/portfolio` → 200
```json
{ "cash": 990000.00, "reservedCash": 0.00, "holdingsValue": 20603.75, "totalValue": 1010603.75,
  "netDeposits": 1000000.00, "totalPnl": 10603.75, "totalPnlPercent": 1.06,
  "holdings": [ { "symbol":"TCS","name":"Tata Consultancy Services","sector":"IT","quantity":5,"reservedQuantity":0,
                  "availableQuantity":5,"avgBuyPrice":4120.75,"currentPrice":4120.75,"costBasis":20603.75,
                  "marketValue":20603.75,"unrealizedPnl":0.00,"unrealizedPnlPercent":0.00 } ] }
```

## Leaderboard (any logged-in user)
GET `/api/leaderboard` → `[{"rank":1,"userId":2,"username":"karthik","totalValue":1010603.75,"totalPnl":10603.75,"totalPnlPercent":1.06}]` (traders only, sorted by totalValue desc).

## Admin (ADMIN only)
| Method | Path | Body / Response |
|---|---|---|
| GET | `/api/admin/users` | `[{"id","username","role","createdAt","cash"(null for admin),"holdingsValue"(null for admin),"totalValue"(null for admin),"ledgerConsistent"(null for admin)}]` |
| POST | `/api/admin/users/{id}/deposit` | `{"amount": 50000.00}` (0 < amount ≤ 10,000,000) → 200 `WalletResponse` |
| POST | `/api/admin/users/{id}/reset` | – → 200 `WalletResponse` (cancels pending orders, clears holdings, cash back to ₹10,00,000) |
| POST | `/api/admin/stocks` | `{"symbol":"WIPRO","name":"Wipro","sector":"IT","price":550.00}` (symbol 1-16 `[A-Z0-9&]`, uppercased by server) → 201 `StockResponse` |
| DELETE | `/api/admin/stocks/{symbol}` | 204 (delists: cancels pending orders, settles holdings at last price) |
