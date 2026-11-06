/* ==========================================================================
   TradeSim — Modern Fintech Client (SSE Streaming + High-DPI Canvas + ACID API)
   100% Aligned with Spring Boot DTO Contracts & Bearer Session Security
   ========================================================================== */

(() => {
  "use strict";

  const $ = (id) => document.getElementById(id);

  const BRAND_PALETTE = [
    "#F59E0B", "#10B981", "#6366F1", "#0EA5E9",
    "#EC4899", "#8B5CF6", "#14B8A6", "#F97316"
  ];

  function readCleanStoredToken() {
    const raw = localStorage.getItem("tradesim_token");
    if (!raw || raw === "undefined" || raw === "null" || raw.trim().length < 10) {
      localStorage.removeItem("tradesim_token");
      return null;
    }
    return raw.trim();
  }

  const state = {
    token: readCleanStoredToken(),
    theme: localStorage.getItem("tradesim_ui_theme") || "light",
    authMode: "login",
    user: null,
    activeView: "dashboard",
    stocks: [],
    prevPrices: new Map(),
    sparkCache: new Map(),
    selectedSymbol: "RELIANCE",
    selectedSector: "ALL",
    searchQuery: "",
    chartMode: "line", // 'line' | 'candle' | 'depth'
    chartHistory: [],
    hoverPointIdx: null,
    orderType: "MARKET", // 'MARKET' | 'LIMIT'
    portfolio: null,
    wallet: null,
    orders: [],
    leaderboard: [],
    adminUsers: [],
    notifications: [],
    unreadNotifs: true,
    depositTargetUser: null,
    sse: null,
    pollTimer: null
  };

  function isTrader() {
    return Boolean(state.user && state.user.role === "TRADER");
  }

  function isAdmin() {
    return Boolean(state.user && state.user.role === "ADMIN");
  }

  /* ========================================================================
     FORMATTERS & COLOR HELPERS
     ======================================================================== */

  const inrFmt = new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  const numFmt = new Intl.NumberFormat("en-IN", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  function fmtInr(val) {
    const n = Number(val ?? 0);
    return inrFmt.format(Number.isFinite(n) ? n : 0);
  }

  function fmtCompactInr(val) {
    const n = Math.abs(Number(val ?? 0));
    const sign = Number(val ?? 0) < 0 ? "-" : "";
    if (n >= 10000000) return `${sign}₹${(n / 10000000).toFixed(2)}Cr`;
    if (n >= 100000) return `${sign}₹${(n / 100000).toFixed(2)}L`;
    if (n >= 1000) return `${sign}₹${(n / 1000).toFixed(1)}k`;
    return fmtInr(val);
  }

  function fmtPct(val) {
    const n = Number(val ?? 0);
    const safe = Number.isFinite(n) ? n : 0;
    const arrow = safe >= 0 ? "▲" : "▼";
    const sign = safe >= 0 ? "+" : "";
    return `${arrow} ${sign}${safe.toFixed(2)}%`;
  }

  function fmtTimeShort(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    return d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
  }

  function fmtDateShort(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
  }

  function symbolColor(symbol) {
    let hash = 0;
    const s = String(symbol || "T");
    for (let i = 0; i < s.length; i++) {
      hash = (hash * 31 + s.charCodeAt(i)) >>> 0;
    }
    return BRAND_PALETTE[hash % BRAND_PALETTE.length];
  }

  function symbolInitials(symbol) {
    const s = String(symbol || "TS");
    return s.slice(0, 2).toUpperCase();
  }

  function escapeHtml(str) {
    return String(str ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  /* ========================================================================
     HTTP API, BEARER AUTH HANDLING & TOASTS
     ======================================================================== */

  function forceResetToAuthScreen(reasonMsg) {
    localStorage.removeItem("tradesim_token");
    state.token = null;
    state.user = null;
    state.portfolio = null;
    state.wallet = null;
    state.orders = [];
    $("appShell").hidden = true;
    $("authScreen").hidden = false;
    renderAuthLiveMovers();
    if (reasonMsg) {
      showToast(reasonMsg, true);
    }
  }

  async function api(path, options = {}) {
    const headers = Object.assign({}, options.headers || {});
    if (options.body && !headers["Content-Type"]) {
      headers["Content-Type"] = "application/json";
    }
    if (state.token) {
      headers["Authorization"] = `Bearer ${state.token}`;
    }

    const resp = await fetch(path, { ...options, headers });
    if (resp.status === 204) return null;

    const contentType = resp.headers.get("content-type") || "";
    const body = contentType.includes("application/json")
      ? await resp.json().catch(() => ({}))
      : await resp.text();

    if (!resp.ok) {
      const msg = (body && body.message) || (typeof body === "string" && body) || `Request failed (${resp.status})`;
      const isPublicAuthCall = path.startsWith("/api/auth/login") || path.startsWith("/api/auth/register");
      if (resp.status === 401 && !isPublicAuthCall && state.user) {
        forceResetToAuthScreen("Session expired — please sign in or launch an instant demo.");
      }
      const err = new Error(msg);
      err.status = resp.status;
      throw err;
    }
    return body;
  }

  function showToast(message, isError = false) {
    const region = $("toastRegion");
    if (!region) return;
    const card = document.createElement("div");
    card.className = `toast-card${isError ? " error" : ""}`;
    card.textContent = message;
    region.appendChild(card);
    setTimeout(() => {
      card.remove();
    }, 3600);
  }

  function pushNotification(title, detail, symbol = "₹", color = "#10B981") {
    state.notifications.unshift({
      id: Date.now() + Math.random(),
      title,
      detail,
      symbol,
      color,
      time: new Date().toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })
    });
    state.notifications = state.notifications.slice(0, 12);
    state.unreadNotifs = true;
    renderNotifications();
  }

  /* ========================================================================
     THEME MANAGEMENT
     ======================================================================== */

  function applyTheme(theme) {
    state.theme = theme === "dark" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", state.theme);
    localStorage.setItem("tradesim_ui_theme", state.theme);
    const lbl = $("themeToggleLabel");
    if (lbl) {
      lbl.textContent = state.theme === "light" ? "Dark Appearance" : "Light Appearance";
    }
    drawMainChart();
    drawMoneyFlowChart();
  }

  /* ========================================================================
     REAL-TIME SSE STREAM + SPARKLINE SERIES
     ======================================================================== */

  function ensureSparkSeries(stock) {
    const sym = stock.symbol;
    const price = Number(stock.price);
    const prev = Number(stock.previousClose || price);
    let arr = state.sparkCache.get(sym);
    if (!arr || arr.length < 2) {
      arr = [];
      let cur = prev;
      let seed = 0;
      for (let i = 0; i < sym.length; i++) seed = (seed * 33 + sym.charCodeAt(i)) % 9973;
      for (let i = 0; i < 20; i++) {
        seed = (seed * 9301 + 49297) % 233280;
        const rnd = seed / 233280 - 0.5;
        const pull = (price - cur) * 0.14;
        cur = Math.max(1, cur + pull + rnd * prev * 0.008);
        arr.push(Number(cur.toFixed(2)));
      }
      arr.push(price);
      state.sparkCache.set(sym, arr);
    } else if (arr[arr.length - 1] !== price) {
      arr.push(price);
      if (arr.length > 28) arr.shift();
    }
    return arr;
  }

  function renderSparklineSvg(points, positive, uid) {
    const w = 118;
    const h = 36;
    if (!points || points.length < 2) return "";
    const min = Math.min(...points);
    const max = Math.max(...points);
    const span = Math.max(max - min, 0.01);
    const pad = 4;

    const coords = points.map((v, idx) => {
      const x = (idx / (points.length - 1)) * w;
      const y = h - pad - ((v - min) / span) * (h - pad * 2);
      return [x, y];
    });

    let d = `M ${coords[0][0].toFixed(1)} ${coords[0][1].toFixed(1)}`;
    for (let i = 1; i < coords.length; i++) {
      const prev = coords[i - 1];
      const curr = coords[i];
      const cx = ((prev[0] + curr[0]) / 2).toFixed(1);
      d += ` Q ${prev[0].toFixed(1)} ${prev[1].toFixed(1)}, ${cx} ${((prev[1] + curr[1]) / 2).toFixed(1)}`;
    }
    const last = coords[coords.length - 1];
    d += ` T ${last[0].toFixed(1)} ${last[1].toFixed(1)}`;

    const areaD = `${d} L ${w} ${h} L 0 ${h} Z`;
    const stroke = positive ? "#16A34A" : "#EF4444";
    const gradId = `spk_${uid}_${positive ? "up" : "dn"}`;

    return `
      <svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}" fill="none" aria-hidden="true">
        <defs>
          <linearGradient id="${gradId}" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stop-color="${stroke}" stop-opacity="0.24"/>
            <stop offset="100%" stop-color="${stroke}" stop-opacity="0.0"/>
          </linearGradient>
        </defs>
        <path d="${areaD}" fill="url(#${gradId})"/>
        <path d="${d}" stroke="${stroke}" stroke-width="2" stroke-linecap="round" fill="none"/>
      </svg>
    `;
  }

  function connectMarketStream() {
    if (state.sse) {
      state.sse.close();
      state.sse = null;
    }
    try {
      const es = new EventSource("/api/stream/market");
      state.sse = es;

      es.addEventListener("quotes", (ev) => {
        try {
          const quotes = JSON.parse(ev.data);
          onMarketQuotesUpdate(quotes);
          const dot = $("streamDot");
          if (dot) dot.classList.remove("offline");
        } catch (_) {}
      });

      es.onerror = () => {
        const dot = $("streamDot");
        if (dot) dot.classList.add("offline");
        if (!state.pollTimer) startFallbackPolling();
      };
    } catch (_) {
      startFallbackPolling();
    }
  }

  function startFallbackPolling() {
    if (state.pollTimer) clearInterval(state.pollTimer);
    state.pollTimer = setInterval(async () => {
      try {
        const quotes = await api("/api/stocks");
        onMarketQuotesUpdate(quotes);
      } catch (_) {}
    }, 3000);
  }

  function onMarketQuotesUpdate(quotes) {
    if (!Array.isArray(quotes) || !quotes.length) return;

    const flashMap = new Map();
    for (const q of quotes) {
      const prev = state.prevPrices.get(q.symbol);
      const curr = Number(q.price);
      if (prev !== undefined && curr !== prev) {
        flashMap.set(q.symbol, curr > prev ? "up" : "down");
      }
      state.prevPrices.set(q.symbol, curr);
      ensureSparkSeries(q);
    }

    state.stocks = quotes;
    if (!state.stocks.some((s) => s.symbol === state.selectedSymbol)) {
      state.selectedSymbol = state.stocks[0].symbol;
    }

    const activeStock = getSelectedStock();
    if (activeStock && state.chartHistory.length > 0) {
      const lastPt = state.chartHistory[state.chartHistory.length - 1];
      if (Number(lastPt.price) !== Number(activeStock.price)) {
        state.chartHistory.push({
          price: Number(activeStock.price),
          recordedAt: new Date().toISOString()
        });
        if (state.chartHistory.length > 96) state.chartHistory.shift();
      }
    }

    renderAuthLiveMovers();
    if (!state.user || !state.token) return;

    populateSymbolSelectors();
    renderSectorPills();
    updateHeroChartQuote();
    if (state.chartMode === "depth") {
      renderOrderBookLadder();
    } else {
      drawMainChart();
    }
    renderDashboardMarketTrend(flashMap);
    if (state.orderType === "MARKET" && activeStock) {
      $("orderPriceInput").value = Number(activeStock.price).toFixed(2);
      updateOrderEstimate();
    }
    if (state.activeView === "market") {
      renderFullMarketTable();
    }
    updateConversionOutput();

    if (isTrader()) {
      const hasPendingLimits = state.orders.some((o) => o.status === "PENDING");
      if (hasPendingLimits) {
        refreshTraderStateQuietly();
      } else if (state.portfolio) {
        recomputeLivePortfolioMetrics();
      }
    }
  }

  let quietRefreshInFlight = false;
  async function refreshTraderStateQuietly() {
    if (quietRefreshInFlight || !isTrader() || !state.token) return;
    quietRefreshInFlight = true;
    try {
      const prevPendingIds = new Set(state.orders.filter((o) => o.status === "PENDING").map((o) => o.id));
      const [portfolio, wallet, orders] = await Promise.all([
        api("/api/portfolio"),
        api("/api/wallet"),
        api(`/api/orders${buildOrderFilterQuery()}`)
      ]);
      state.portfolio = portfolio;
      state.wallet = wallet;
      state.orders = orders || [];

      for (const o of state.orders) {
        if (prevPendingIds.has(o.id) && o.status === "FILLED") {
          const fillPx = o.executedPrice ?? o.limitPrice;
          showToast(`Limit ${o.side} filled: ${o.quantity} ${o.symbol} @ ${fmtInr(fillPx)}`);
          pushNotification(
            `Limit ${o.side} Executed`,
            `${o.quantity} ${o.symbol} filled at ${fmtInr(fillPx)}`,
            symbolInitials(o.symbol),
            symbolColor(o.symbol)
          );
        }
      }

      renderBalanceAndPortfolioUI();
      renderOrdersTable();
      renderWalletView();
    } catch (_) {
    } finally {
      quietRefreshInFlight = false;
    }
  }

  function recomputeLivePortfolioMetrics() {
    if (!state.portfolio || !Array.isArray(state.portfolio.holdings)) return;
    const priceBySym = new Map(state.stocks.map((s) => [s.symbol, Number(s.price)]));
    let totalEquity = 0;

    for (const pos of state.portfolio.holdings) {
      const livePrice = priceBySym.get(pos.symbol) ?? Number(pos.currentPrice);
      pos.currentPrice = livePrice;
      pos.marketValue = livePrice * Number(pos.quantity);
      const cost = Number(pos.costBasis ?? Number(pos.avgBuyPrice) * Number(pos.quantity));
      pos.unrealizedPnl = pos.marketValue - cost;
      pos.unrealizedPnlPercent = cost > 0 ? (pos.unrealizedPnl / cost) * 100 : 0;
      totalEquity += pos.marketValue;
    }

    state.portfolio.holdingsValue = totalEquity;
    const totalCash = Number(state.portfolio.cash || 0) + Number(state.portfolio.reservedCash || 0);
    state.portfolio.totalValue = totalCash + totalEquity;
    const deposits = Number(state.portfolio.netDeposits || 1000000);
    state.portfolio.totalPnl = state.portfolio.totalValue - deposits;
    state.portfolio.totalPnlPercent = deposits > 0 ? (state.portfolio.totalPnl / deposits) * 100 : 0;

    renderBalanceAndPortfolioUI();
    if (state.activeView === "market") {
      renderOpenPositionsTable();
    }
  }

  /* ========================================================================
     AUTH FLOW & 1-CLICK INSTANT DEMOS (AUTO-LOGINS AFTER REGISTER!)
     ======================================================================== */

  function renderAuthLiveMovers() {
    const box = $("authMoverList");
    if (!box) return;
    const countEl = $("authFeedCount");
    if (countEl) countEl.textContent = `${state.stocks.length} NSE Equities`;

    const top = state.stocks.slice(0, 4);
    box.innerHTML = top
      .map((s) => {
        const up = Number(s.changePercent) >= 0;
        const pts = ensureSparkSeries(s);
        return `
          <div class="auth-mover-row tabular">
            <div class="trend-asset-cell">
              <span class="coin-avatar-sm" style="background:${symbolColor(s.symbol)}">${escapeHtml(symbolInitials(s.symbol))}</span>
              <div class="trend-asset-meta">
                <strong>${escapeHtml(s.symbol)}</strong>
                <small>${escapeHtml(s.sector)}</small>
              </div>
            </div>
            <div>${renderSparklineSvg(pts, up, `auth_${s.symbol}`)}</div>
            <div class="trend-price-cell">
              <strong>${fmtInr(s.price)}</strong>
              <small class="${up ? "up" : "down"}">${fmtPct(s.changePercent)}</small>
            </div>
          </div>
        `;
      })
      .join("");
  }

  function setAuthMode(mode) {
    state.authMode = mode === "register" ? "register" : "login";
    $("tabLogin").classList.toggle("active", state.authMode === "login");
    $("tabLogin").setAttribute("aria-selected", String(state.authMode === "login"));
    $("tabRegister").classList.toggle("active", state.authMode === "register");
    $("tabRegister").setAttribute("aria-selected", String(state.authMode === "register"));
    $("authSubmitBtn").textContent =
      state.authMode === "login" ? "Sign In to Dashboard" : "Create Account (₹10,00,000)";
    $("authErrorBox").hidden = true;
  }

  async function registerAndEnsureSession(username, password) {
    const regRes = await api("/api/auth/register", {
      method: "POST",
      body: JSON.stringify({ username, password })
    });
    // Works whether backend returns LoginResponse {token, user} OR UserResponse {id, username, role}
    if (regRes && regRes.token && regRes.user) {
      return regRes;
    }
    return await api("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password })
    });
  }

  async function handleAuthSubmit(ev) {
    ev.preventDefault();
    const errBox = $("authErrorBox");
    errBox.hidden = true;
    const username = $("authUsername").value.trim();
    const password = $("authPassword").value;

    if (username.length < 3 || password.length < 6) {
      errBox.textContent = "Enter a username (3–30 chars, letters/digits/_) and password (6+ chars).";
      errBox.hidden = false;
      return;
    }

    try {
      const loginRes =
        state.authMode === "register"
          ? await registerAndEnsureSession(username, password)
          : await api("/api/auth/login", {
              method: "POST",
              body: JSON.stringify({ username, password })
            });
      await completeLogin(loginRes);
      if (state.authMode === "register") {
        showToast(`Account @${username} created with ₹10,00,000.00 starter cash!`);
      }
    } catch (err) {
      errBox.textContent = err.message;
      errBox.hidden = false;
    }
  }

  async function launchInstantTraderDemo() {
    const suffix = Math.floor(10000 + Math.random() * 90000);
    const username = `trader_${suffix}`;
    const password = `demo_${suffix}_pass`;
    try {
      const loginRes = await registerAndEnsureSession(username, password);
      await completeLogin(loginRes);
      showToast(`Instant Demo active! ₹10,00,000.00 credited to @${username}`);
    } catch (err) {
      showToast(err.message, true);
    }
  }

  async function launchInstantAdminDemo() {
    try {
      const res = await api("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: "admin", password: "admin123" })
      });
      await completeLogin(res);
      showToast("Signed in as Exchange Administrator");
    } catch (_) {
      setAuthMode("login");
      $("authUsername").value = "admin";
      $("authPassword").focus();
      showToast("Enter your configured ADMIN_PASSWORD to sign in as admin.", true);
    }
  }

  async function completeLogin(authRes) {
    if (!authRes || !authRes.token || !authRes.user) {
      throw new Error("Could not establish authenticated session token.");
    }
    state.token = authRes.token;
    state.user = authRes.user;
    localStorage.setItem("tradesim_token", state.token);
    await enterAuthenticatedShell();
  }

  async function enterAuthenticatedShell() {
    $("authScreen").hidden = true;
    $("appShell").hidden = false;

    const u = state.user;
    $("userDisplayName").textContent = u.username;
    $("userRoleLabel").textContent = u.role;
    $("userAvatarBadge").textContent = u.username.slice(0, 1).toUpperCase();

    // Role-based navigation visibility (Admins manage the exchange; Traders have wallets & orders)
    $("navAdminBtn").hidden = !isAdmin();
    document.querySelectorAll('.nav-item[data-view="wallet"], .nav-item[data-view="orders"]').forEach((el) => {
      el.hidden = !isTrader();
    });

    $("buyActionBtn").disabled = !isTrader();
    $("sellActionBtn").disabled = !isTrader();

    $("vcardHolderName").textContent = u.username;
    const acctTail = String(1000 + ((u.id || 7) * 7919) % 9000);
    $("vcardAccountNum").textContent = `4532  8810  4092  ${acctTail}`;

    state.notifications = [];
    await loadAllWorkspaceData();
    await selectSymbol(state.selectedSymbol, false);
    seedStarterNotifications();
    switchView(isAdmin() ? "admin" : "dashboard");
  }

  async function handleLogout() {
    try {
      if (state.token) {
        await api("/api/auth/logout", { method: "POST" });
      }
    } catch (_) {}
    forceResetToAuthScreen();
  }

  /* ========================================================================
     WORKSPACE DATA LOADING & NAVIGATION
     ======================================================================== */

  async function loadAllWorkspaceData() {
    if (isAdmin()) {
      const [stocks, leaderboard, adminUsers] = await Promise.all([
        api("/api/stocks"),
        api("/api/leaderboard"),
        api("/api/admin/users")
      ]);
      state.stocks = stocks || [];
      state.leaderboard = leaderboard || [];
      state.adminUsers = adminUsers || [];
      state.portfolio = null;
      state.wallet = null;
      state.orders = [];
    } else {
      const [stocks, portfolio, wallet, orders, leaderboard] = await Promise.all([
        api("/api/stocks"),
        api("/api/portfolio"),
        api("/api/wallet"),
        api(`/api/orders${buildOrderFilterQuery()}`),
        api("/api/leaderboard")
      ]);
      state.stocks = stocks || [];
      state.portfolio = portfolio;
      state.wallet = wallet;
      state.orders = orders || [];
      state.leaderboard = leaderboard || [];
      state.adminUsers = [];
    }

    for (const s of state.stocks) {
      state.prevPrices.set(s.symbol, Number(s.price));
      ensureSparkSeries(s);
    }

    populateSymbolSelectors();
    renderSectorPills();
    renderDashboardMarketTrend(new Map());
    renderBalanceAndPortfolioUI();
    renderFullMarketTable();
    renderOpenPositionsTable();
    if (isTrader()) {
      renderWalletView();
      renderOrdersTable();
    }
    renderLeaderboardView();
    if (isAdmin()) {
      renderAdminView();
    }
    updateConversionOutput();
  }

  function switchView(viewName) {
    if ((viewName === "wallet" || viewName === "orders") && !isTrader()) {
      viewName = "admin";
    }
    if (viewName === "admin" && !isAdmin()) {
      viewName = "dashboard";
    }

    state.activeView = viewName;
    document.querySelectorAll(".nav-item[data-view]").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.view === viewName);
    });
    document.querySelectorAll(".view-section").forEach((sec) => {
      const match = sec.id === `view-${viewName}`;
      sec.hidden = !match;
      sec.classList.toggle("active", match);
    });

    if (viewName === "dashboard") {
      requestAnimationFrame(() => drawMainChart());
    } else if (viewName === "wallet" && isTrader()) {
      renderWalletView();
      requestAnimationFrame(() => drawMoneyFlowChart());
    } else if (viewName === "market") {
      renderFullMarketTable();
      renderOpenPositionsTable();
    } else if (viewName === "orders" && isTrader()) {
      refreshOrdersOnly();
    } else if (viewName === "leaderboard") {
      refreshLeaderboardOnly();
    } else if (viewName === "admin" && isAdmin()) {
      refreshAdminOnly();
    }
  }

  async function refreshOrdersOnly() {
    if (!isTrader()) return;
    try {
      state.orders = (await api(`/api/orders${buildOrderFilterQuery()}`)) || [];
      renderOrdersTable();
    } catch (_) {}
  }

  async function refreshLeaderboardOnly() {
    try {
      state.leaderboard = (await api("/api/leaderboard")) || [];
      renderLeaderboardView();
    } catch (_) {}
  }

  async function refreshAdminOnly() {
    if (!isAdmin()) return;
    try {
      const [stocks, users] = await Promise.all([api("/api/stocks"), api("/api/admin/users")]);
      state.stocks = stocks || [];
      state.adminUsers = users || [];
      renderAdminView();
    } catch (_) {}
  }

  /* ========================================================================
     SYMBOL SELECTION & INTERACTIVE HIGH-DPI CANVAS CHART
     ======================================================================== */

  function getSelectedStock() {
    return state.stocks.find((s) => s.symbol === state.selectedSymbol) || state.stocks[0] || null;
  }

  function populateSymbolSelectors() {
    const heroSel = $("symbolSelectDropdown");
    const convSel = $("convSymbolSelect");
    if (!heroSel || !convSel || !state.stocks.length) return;

    const optionsHtml = state.stocks
      .map((s) => `<option value="${escapeHtml(s.symbol)}">${escapeHtml(s.symbol)} · ₹${Number(s.price).toFixed(2)}</option>`)
      .join("");

    const prevConv = convSel.value || state.selectedSymbol;
    heroSel.innerHTML = optionsHtml;
    convSel.innerHTML = state.stocks
      .map((s) => `<option value="${escapeHtml(s.symbol)}">${escapeHtml(s.symbol)}</option>`)
      .join("");

    heroSel.value = state.selectedSymbol;
    convSel.value = state.stocks.some((s) => s.symbol === prevConv) ? prevConv : state.selectedSymbol;

    const badge = $("activeSymbolBadge");
    if (badge) {
      badge.textContent = symbolInitials(state.selectedSymbol);
      badge.style.background = symbolColor(state.selectedSymbol);
    }
  }

  async function selectSymbol(symbol, focusTicket = false) {
    if (!symbol) return;
    state.selectedSymbol = symbol;
    state.hoverPointIdx = null;
    populateSymbolSelectors();

    const stock = getSelectedStock();
    if (stock) {
      $("ticketTitleSymbol").textContent = `Trade ${stock.symbol}`;
      $("buyActionBtn").textContent = isTrader() ? `Buy ${stock.symbol}` : "Admin View Only";
      $("sellActionBtn").textContent = isTrader() ? `Sell ${stock.symbol}` : "Switch to Trader Account";
      $("orderPriceInput").value = Number(stock.price).toFixed(2);
      updateOrderEstimate();
      updateHeroChartQuote();
      renderBalanceAndPortfolioUI();
    }

    try {
      const history = await api(`/api/stocks/${encodeURIComponent(symbol)}/history?limit=72`);
      state.chartHistory = Array.isArray(history) && history.length
        ? history
        : (stock ? [{ price: Number(stock.price), recordedAt: new Date().toISOString() }] : []);
    } catch (_) {
      state.chartHistory = stock ? [{ price: Number(stock.price), recordedAt: new Date().toISOString() }] : [];
    }

    renderDashboardMarketTrend(new Map());
    if (state.chartMode === "depth") {
      renderOrderBookLadder();
    } else {
      drawMainChart();
    }

    if (focusTicket) {
      switchView("dashboard");
      $("orderQtyInput").focus();
    }
  }

  function updateHeroChartQuote() {
    const stock = getSelectedStock();
    if (!stock) return;

    $("heroStockName").textContent = `${stock.name} (${stock.symbol})`;
    $("heroStockPrice").textContent = fmtInr(stock.price);
    $("heroStockSector").textContent = stock.sector;

    const pill = $("heroStockChangePill");
    const up = Number(stock.changePercent) >= 0;
    pill.className = `badge-pill ${up ? "positive" : "negative"} tabular`;
    pill.textContent = fmtPct(stock.changePercent);

    const prices = state.chartHistory.length
      ? state.chartHistory.map((p) => Number(p.price))
      : [Number(stock.price), Number(stock.previousClose)];

    $("heroPrevClose").textContent = fmtInr(stock.previousClose);
    $("heroSessionHigh").textContent = fmtInr(Math.max(...prices, Number(stock.price)));
    $("heroSessionLow").textContent = fmtInr(Math.min(...prices, Number(stock.price)));
  }

  function setChartMode(mode) {
    state.chartMode = mode;
    document.querySelectorAll(".pill-tab[data-chart-mode]").forEach((btn) => {
      btn.classList.toggle("active", btn.dataset.chartMode === mode);
    });

    const canvasWrap = $("chartCanvasWrap");
    const depthPane = $("embeddedDepthPane");
    if (mode === "depth") {
      canvasWrap.hidden = true;
      depthPane.hidden = false;
      renderOrderBookLadder();
    } else {
      depthPane.hidden = true;
      canvasWrap.hidden = false;
      drawMainChart();
    }
  }

  function drawMainChart() {
    const canvas = $("mainPriceCanvas");
    if (!canvas || canvas.offsetParent === null) return;

    const rect = canvas.parentElement.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const width = Math.max(300, rect.width);
    const height = Math.max(220, rect.height);

    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    const ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, width, height);

    const raw = state.chartHistory.length
      ? state.chartHistory
      : [{ price: Number(getSelectedStock()?.price || 1000), recordedAt: new Date().toISOString() }];

    const isDark = state.theme === "dark";
    const gridColor = isDark ? "rgba(148, 163, 184, 0.12)" : "#EEF1F6";
    const axisText = isDark ? "#94A3B8" : "#8A94A6";
    const linePrimary = isDark ? "#F8FAFC" : "#111318";

    const padLeft = 14;
    const padRight = 68;
    const padTop = 26;
    const padBottom = 28;
    const plotW = width - padLeft - padRight;
    const plotH = height - padTop - padBottom;

    if (state.chartMode === "candle") {
      drawCandlestickSeries(ctx, raw, padLeft, padTop, plotW, plotH, width, height, gridColor, axisText);
      return;
    }

    const vals = raw.map((p) => Number(p.price));
    let minP = Math.min(...vals);
    let maxP = Math.max(...vals);
    if (maxP - minP < 0.5) {
      minP -= 2;
      maxP += 2;
    } else {
      const margin = (maxP - minP) * 0.14;
      minP -= margin;
      maxP += margin;
    }

    const xAt = (i) => padLeft + (raw.length === 1 ? plotW / 2 : (i / (raw.length - 1)) * plotW);
    const yAt = (v) => padTop + (1 - (v - minP) / (maxP - minP)) * plotH;

    ctx.font = "600 11px 'Plus Jakarta Sans', sans-serif";
    ctx.fillStyle = axisText;
    ctx.textAlign = "left";
    const steps = 4;
    for (let s = 0; s <= steps; s++) {
      const y = padTop + (s / steps) * plotH;
      const priceVal = maxP - (s / steps) * (maxP - minP);
      ctx.save();
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = gridColor;
      ctx.beginPath();
      ctx.moveTo(padLeft, y);
      ctx.lineTo(padLeft + plotW, y);
      ctx.stroke();
      ctx.restore();
      ctx.fillText(`₹${numFmt.format(priceVal)}`, padLeft + plotW + 8, y + 4);
    }

    const pts = vals.map((v, i) => ({ x: xAt(i), y: yAt(v), v, t: raw[i].recordedAt }));

    const grad = ctx.createLinearGradient(0, padTop, 0, padTop + plotH);
    grad.addColorStop(0, isDark ? "rgba(248, 250, 252, 0.14)" : "rgba(17, 19, 24, 0.08)");
    grad.addColorStop(1, "rgba(17, 19, 24, 0.0)");

    ctx.beginPath();
    ctx.moveTo(pts[0].x, pts[0].y);
    for (let i = 1; i < pts.length; i++) {
      const prev = pts[i - 1];
      const curr = pts[i];
      const midX = (prev.x + curr.x) / 2;
      const midY = (prev.y + curr.y) / 2;
      ctx.quadraticCurveTo(prev.x, prev.y, midX, midY);
    }
    const last = pts[pts.length - 1];
    ctx.lineTo(last.x, last.y);
    ctx.lineTo(last.x, padTop + plotH);
    ctx.lineTo(pts[0].x, padTop + plotH);
    ctx.closePath();
    ctx.fillStyle = grad;
    ctx.fill();

    ctx.beginPath();
    ctx.moveTo(pts[0].x, pts[0].y);
    for (let i = 1; i < pts.length; i++) {
      const prev = pts[i - 1];
      const curr = pts[i];
      const midX = (prev.x + curr.x) / 2;
      const midY = (prev.y + curr.y) / 2;
      ctx.quadraticCurveTo(prev.x, prev.y, midX, midY);
    }
    ctx.lineTo(last.x, last.y);
    ctx.strokeStyle = linePrimary;
    ctx.lineWidth = 2.6;
    ctx.lineCap = "round";
    ctx.stroke();

    // Draw resting PENDING limit orders on this symbol (uses OrderResponse.limitPrice!)
    const pendingLimits = state.orders.filter(
      (o) => o.status === "PENDING" && o.symbol === state.selectedSymbol && o.limitPrice != null
    );
    for (const lim of pendingLimits) {
      const lp = Number(lim.limitPrice);
      if (lp >= minP && lp <= maxP) {
        const ly = yAt(lp);
        ctx.save();
        ctx.setLineDash([5, 4]);
        ctx.strokeStyle = lim.side === "BUY" ? "#16A34A" : "#EF4444";
        ctx.lineWidth = 1.4;
        ctx.beginPath();
        ctx.moveTo(padLeft, ly);
        ctx.lineTo(padLeft + plotW, ly);
        ctx.stroke();
        ctx.restore();
      }
    }

    ctx.fillStyle = axisText;
    ctx.textAlign = "center";
    const tickCount = Math.min(6, pts.length);
    for (let k = 0; k < tickCount; k++) {
      const idx = Math.floor((k / Math.max(1, tickCount - 1)) * (pts.length - 1));
      const p = pts[idx];
      ctx.fillText(fmtTimeShort(p.t).slice(0, 5), p.x, height - 7);
    }

    let targetIdx = state.hoverPointIdx;
    if (targetIdx === null || targetIdx < 0 || targetIdx >= pts.length) {
      let bestIdx = pts.length - 1;
      let bestVal = -Infinity;
      const startScan = Math.floor(pts.length * 0.15);
      const endScan = Math.max(startScan + 1, Math.floor(pts.length * 0.85));
      for (let i = startScan; i < endScan; i++) {
        if (pts[i].v > bestVal) {
          bestVal = pts[i].v;
          bestIdx = i;
        }
      }
      targetIdx = bestIdx;
    }

    const focusPt = pts[targetIdx];
    if (focusPt) {
      if (state.hoverPointIdx !== null) {
        ctx.save();
        ctx.setLineDash([3, 3]);
        ctx.strokeStyle = axisText;
        ctx.beginPath();
        ctx.moveTo(focusPt.x, padTop);
        ctx.lineTo(focusPt.x, padTop + plotH);
        ctx.stroke();
        ctx.restore();
      }

      ctx.beginPath();
      ctx.arc(focusPt.x, focusPt.y, 5.5, 0, Math.PI * 2);
      ctx.fillStyle = isDark ? "#131722" : "#FFFFFF";
      ctx.fill();
      ctx.lineWidth = 2.5;
      ctx.strokeStyle = linePrimary;
      ctx.stroke();

      const label = fmtInr(focusPt.v);
      ctx.font = "700 11.5px 'Plus Jakarta Sans', sans-serif";
      const tw = ctx.measureText(label).width + 16;
      const th = 24;
      const bx = Math.min(Math.max(padLeft, focusPt.x - tw / 2), padLeft + plotW - tw);
      const by = Math.max(4, focusPt.y - 34);

      ctx.fillStyle = linePrimary;
      roundRect(ctx, bx, by, tw, th, 7);
      ctx.fill();

      ctx.fillStyle = isDark ? "#0F172A" : "#FFFFFF";
      ctx.textAlign = "center";
      ctx.fillText(label, bx + tw / 2, by + 16);
    }
  }

  function drawCandlestickSeries(ctx, raw, padLeft, padTop, plotW, plotH, width, height, gridColor, axisText) {
    const bucketSize = Math.max(1, Math.ceil(raw.length / 24));
    const candles = [];
    for (let i = 0; i < raw.length; i += bucketSize) {
      const slice = raw.slice(i, i + bucketSize).map((x) => Number(x.price));
      const open = i > 0 ? Number(raw[i - 1].price) : slice[0];
      const close = slice[slice.length - 1];
      const high = Math.max(open, ...slice) + open * 0.0012;
      const low = Math.min(open, ...slice) - open * 0.0012;
      candles.push({ open, high, low, close, time: raw[i].recordedAt });
    }

    let minP = Math.min(...candles.map((c) => c.low));
    let maxP = Math.max(...candles.map((c) => c.high));
    const pad = Math.max(0.5, (maxP - minP) * 0.12);
    minP -= pad;
    maxP += pad;

    const yAt = (v) => padTop + (1 - (v - minP) / (maxP - minP)) * plotH;

    ctx.font = "600 11px 'Plus Jakarta Sans', sans-serif";
    ctx.fillStyle = axisText;
    ctx.textAlign = "left";
    for (let s = 0; s <= 4; s++) {
      const y = padTop + (s / 4) * plotH;
      const val = maxP - (s / 4) * (maxP - minP);
      ctx.save();
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = gridColor;
      ctx.beginPath();
      ctx.moveTo(padLeft, y);
      ctx.lineTo(padLeft + plotW, y);
      ctx.stroke();
      ctx.restore();
      ctx.fillText(`₹${numFmt.format(val)}`, padLeft + plotW + 8, y + 4);
    }

    const stepW = plotW / Math.max(1, candles.length);
    const bodyW = Math.max(5, Math.min(14, stepW * 0.56));

    candles.forEach((c, idx) => {
      const cx = padLeft + idx * stepW + stepW / 2;
      const up = c.close >= c.open;
      const color = up ? "#16A34A" : "#EF4444";

      ctx.strokeStyle = color;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.moveTo(cx, yAt(c.high));
      ctx.lineTo(cx, yAt(c.low));
      ctx.stroke();

      const topY = yAt(Math.max(c.open, c.close));
      const botY = yAt(Math.min(c.open, c.close));
      const bh = Math.max(3, botY - topY);

      ctx.fillStyle = color;
      roundRect(ctx, cx - bodyW / 2, topY, bodyW, bh, 2.5);
      ctx.fill();
    });
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /* ========================================================================
     ORDER BOOK LADDER (CLICK TO SNAP LIMIT PRICE)
     ======================================================================== */

  function renderOrderBookLadder() {
    const stock = getSelectedStock();
    if (!stock) return;
    const mid = Number(stock.price);
    const tick = mid >= 1000 ? 0.5 : 0.1;

    const bids = [];
    const asks = [];
    let maxQty = 1;

    for (let lvl = 1; lvl <= 6; lvl++) {
      const seed = (stock.symbol.charCodeAt(0) * 17 + lvl * 37) % 97;
      const bidPrice = Number((mid - lvl * tick).toFixed(2));
      const askPrice = Number((mid + lvl * tick).toFixed(2));
      const bidQty = 12 + ((seed * 3 + lvl * 11) % 85);
      const askQty = 10 + ((seed * 5 + lvl * 13) % 85);
      maxQty = Math.max(maxQty, bidQty, askQty);
      bids.push({ price: bidPrice, qty: bidQty, total: bidPrice * bidQty });
      asks.push({ price: askPrice, qty: askQty, total: askPrice * askQty });
    }

    $("depthSpreadBadge").textContent = `Spread ₹${(asks[0].price - bids[0].price).toFixed(2)}`;

    $("bidLadderRows").innerHTML = bids
      .map(
        (b) => `
        <div class="depth-row bid tabular" data-snap-limit="${b.price}">
          <span class="depth-bar" style="width:${Math.round((b.qty / maxQty) * 100)}%"></span>
          <span class="depth-price">${numFmt.format(b.price)}</span>
          <span>${b.qty}</span>
          <span>${fmtCompactInr(b.total)}</span>
        </div>`
      )
      .join("");

    $("askLadderRows").innerHTML = asks
      .map(
        (a) => `
        <div class="depth-row ask tabular" data-snap-limit="${a.price}">
          <span class="depth-bar" style="width:${Math.round((a.qty / maxQty) * 100)}%"></span>
          <span class="depth-price">${numFmt.format(a.price)}</span>
          <span>${a.qty}</span>
          <span>${fmtCompactInr(a.total)}</span>
        </div>`
      )
      .join("");
  }

  /* ========================================================================
     DASHBOARD: MARKET TREND + PORTFOLIO CARDS + RIGHT RAIL
     (Aligned 100% with PortfolioResponse & HoldingResponse DTOs)
     ======================================================================== */

  function getFilteredStocks() {
    const q = state.searchQuery.trim().toLowerCase();
    return state.stocks.filter((s) => {
      const sectorOk = state.selectedSector === "ALL" || s.sector === state.selectedSector;
      const queryOk =
        !q ||
        s.symbol.toLowerCase().includes(q) ||
        s.name.toLowerCase().includes(q) ||
        s.sector.toLowerCase().includes(q);
      return sectorOk && queryOk;
    });
  }

  function renderSectorPills() {
    const sectors = ["ALL", ...Array.from(new Set(state.stocks.map((s) => s.sector))).sort()];
    const html = sectors
      .map(
        (sec) =>
          `<button type="button" class="sector-pill${state.selectedSector === sec ? " active" : ""}" data-sector="${escapeHtml(sec)}">${
            sec === "ALL" ? "All Sectors" : escapeHtml(sec)
          }</button>`
      )
      .join("");

    if ($("sectorPillsContainer")) $("sectorPillsContainer").innerHTML = html;
    if ($("marketSectorTabs")) $("marketSectorTabs").innerHTML = html;
  }

  function renderDashboardMarketTrend(flashMap) {
    const list = $("dashboardTrendList");
    if (!list) return;

    const filtered = getFilteredStocks().slice(0, 7);
    if (!filtered.length) {
      list.innerHTML = `<p class="muted-xs">No equities match "${escapeHtml(state.searchQuery)}".</p>`;
      return;
    }

    list.innerHTML = filtered
      .map((s) => {
        const up = Number(s.changePercent) >= 0;
        const pts = ensureSparkSeries(s);
        const flash = flashMap.get(s.symbol);
        const flashCls = flash === "up" ? " flash-up" : flash === "down" ? " flash-down" : "";
        const selectedCls = s.symbol === state.selectedSymbol ? " selected" : "";

        return `
          <div class="trend-row tabular${selectedCls}${flashCls}" data-select-symbol="${escapeHtml(s.symbol)}" role="button" tabindex="0">
            <div class="trend-asset-cell">
              <span class="asset-circle-icon" style="background:${symbolColor(s.symbol)}">${escapeHtml(symbolInitials(s.symbol))}</span>
              <div class="trend-asset-meta">
                <strong>${escapeHtml(s.name)}</strong>
                <small>${escapeHtml(s.symbol)} · ${escapeHtml(s.sector)}</small>
              </div>
            </div>
            <div class="trend-spark-cell">
              ${renderSparklineSvg(pts, up, `dash_${s.symbol}`)}
            </div>
            <div class="trend-price-cell">
              <strong>${fmtInr(s.price)}</strong>
              <small class="${up ? "up" : "down"}">${fmtPct(s.changePercent)}</small>
            </div>
          </div>
        `;
      })
      .join("");
  }

  function renderBalanceAndPortfolioUI() {
    if (isAdmin()) {
      $("topNetWorth").textContent = "ADMIN MODE";
      $("railAvailableCash").textContent = "Exchange Admin";
      $("railNetWorth").textContent = `${state.stocks.length} Listed`;
      $("railReservedCash").textContent = `${state.adminUsers.length} Users`;
      $("railSelectedShares").textContent = "—";
      $("railTotalReturnPill").className = "badge-pill neutral";
      $("railTotalReturnPill").textContent = "ADMIN";
      renderFeaturedQuickPickCards();
      $("railAssetsList").innerHTML = `<p class="muted-xs">Use Exchange Admin in the left sidebar to list/delist stocks, credit trader accounts, or reset portfolios.</p>`;
      return;
    }

    const p = state.portfolio;
    if (!p) return;

    const avail = Number(p.cash ?? 0);
    const reserved = Number(p.reservedCash ?? 0);
    const netWorth = Number(p.totalValue ?? avail + reserved);
    const totalRetPct = Number(p.totalPnlPercent ?? 0);
    const holdings = p.holdings || [];

    $("topNetWorth").textContent = fmtInr(netWorth);
    $("railAvailableCash").textContent = fmtInr(avail);
    $("railNetWorth").textContent = fmtCompactInr(netWorth);
    $("railReservedCash").textContent = fmtCompactInr(reserved);

    const retPill = $("railTotalReturnPill");
    retPill.className = `badge-pill ${totalRetPct >= 0 ? "positive" : "negative"} tabular`;
    retPill.textContent = fmtPct(totalRetPct);

    const posForSelected = holdings.find((x) => x.symbol === state.selectedSymbol);
    const availShares = posForSelected ? Number(posForSelected.availableQuantity) : 0;
    $("railSelectedShares").textContent = `${availShares} sh`;

    const cardsGrid = $("dashboardPortfolioCards");
    if (holdings.length > 0) {
      cardsGrid.innerHTML = holdings
        .slice(0, 4)
        .map((pos) => {
          const up = Number(pos.unrealizedPnlPercent) >= 0;
          return `
            <article class="holding-mini-card tabular" data-select-symbol="${escapeHtml(pos.symbol)}">
              <div class="holding-mini-top">
                <div>
                  <small>${escapeHtml(pos.name || pos.symbol)} · ${pos.quantity} sh</small>
                  <strong>${escapeHtml(pos.symbol)}</strong>
                </div>
                <span class="asset-circle-icon" style="background:${symbolColor(pos.symbol)}">${escapeHtml(symbolInitials(pos.symbol))}</span>
              </div>
              <div class="holding-mini-bottom">
                <span class="holding-mini-val">${fmtInr(pos.marketValue)}</span>
                <span class="badge-pill ${up ? "positive" : "negative"}">${fmtPct(pos.unrealizedPnlPercent)}</span>
              </div>
            </article>
          `;
        })
        .join("");
    } else {
      renderFeaturedQuickPickCards();
    }

    renderRailAssetsAndLimits();
  }

  function renderFeaturedQuickPickCards() {
    const cardsGrid = $("dashboardPortfolioCards");
    if (!cardsGrid) return;
    cardsGrid.innerHTML = state.stocks
      .slice(0, 3)
      .map((s) => {
        const up = Number(s.changePercent) >= 0;
        return `
          <article class="holding-mini-card tabular" data-select-symbol="${escapeHtml(s.symbol)}">
            <div class="holding-mini-top">
              <div>
                <small>${escapeHtml(s.name)} · Quick Pick</small>
                <strong>${escapeHtml(s.symbol)}</strong>
              </div>
              <span class="asset-circle-icon" style="background:${symbolColor(s.symbol)}">${escapeHtml(symbolInitials(s.symbol))}</span>
            </div>
            <div class="holding-mini-bottom">
              <span class="holding-mini-val">${fmtInr(s.price)}</span>
              <span class="badge-pill ${up ? "neutral" : "negative"}">${fmtPct(s.changePercent)}</span>
            </div>
          </article>
        `;
      })
      .join("");
  }

  function renderRailAssetsAndLimits() {
    const box = $("railAssetsList");
    if (!box) return;

    const pending = state.orders.filter((o) => o.status === "PENDING");
    const badge = $("navPendingBadge");
    if (badge) {
      badge.hidden = pending.length === 0;
      badge.textContent = String(pending.length);
    }

    const items = [];

    for (const ord of pending.slice(0, 3)) {
      items.push(`
        <div class="rail-asset-item tabular">
          <div class="rail-asset-left">
            <span class="coin-avatar-sm" style="background:${symbolColor(ord.symbol)}">${escapeHtml(symbolInitials(ord.symbol))}</span>
            <div>
              <strong>${escapeHtml(ord.side)} ${ord.quantity} ${escapeHtml(ord.symbol)}</strong>
              <small>Limit @ ${fmtInr(ord.limitPrice)} · Working</small>
            </div>
          </div>
          <button type="button" class="table-btn-sm danger" data-cancel-order="${ord.id}">Cancel</button>
        </div>
      `);
    }

    const holdings = state.portfolio?.holdings || [];
    for (const pos of holdings.slice(0, 3)) {
      const up = Number(pos.unrealizedPnl) >= 0;
      items.push(`
        <div class="rail-asset-item tabular" data-select-symbol="${escapeHtml(pos.symbol)}" style="cursor:pointer">
          <div class="rail-asset-left">
            <span class="coin-avatar-sm" style="background:${symbolColor(pos.symbol)}">${escapeHtml(symbolInitials(pos.symbol))}</span>
            <div>
              <strong>${escapeHtml(pos.symbol)}</strong>
              <small>${pos.quantity} shares · Avg ${fmtInr(pos.avgBuyPrice)}</small>
            </div>
          </div>
          <div class="rail-asset-right">
            <strong>${fmtInr(pos.marketValue)}</strong>
            <span class="badge-pill ${up ? "positive" : "negative"}">${fmtPct(pos.unrealizedPnlPercent)}</span>
          </div>
        </div>
      `);
    }

    if (!items.length) {
      box.innerHTML = `<p class="muted-xs">No active holdings or limit orders yet. Use the ticket above to buy your first NSE shares.</p>`;
    } else {
      box.innerHTML = items.join("");
    }
  }

  /* ========================================================================
     ORDER TICKET (MARKET & LIMIT + POSITION SIZING + BUY/SELL EXECUTION)
     ======================================================================== */

  function setOrderType(type) {
    state.orderType = type === "LIMIT" ? "LIMIT" : "MARKET";
    $("typeMarketBtn").classList.toggle("active", state.orderType === "MARKET");
    $("typeLimitBtn").classList.toggle("active", state.orderType === "LIMIT");

    const priceInp = $("orderPriceInput");
    const snapRow = $("limitSnapRow");
    const helper = $("orderHelperNote");

    const stock = getSelectedStock();
    if (state.orderType === "MARKET") {
      priceInp.readOnly = true;
      if (stock) priceInp.value = Number(stock.price).toFixed(2);
      snapRow.hidden = true;
      helper.textContent = "Executes immediately at current NSE market price with atomic wallet settlement.";
    } else {
      priceInp.readOnly = false;
      if (stock && (!priceInp.value || Number(priceInp.value) <= 0)) {
        priceInp.value = Number(stock.price).toFixed(2);
      }
      snapRow.hidden = false;
      helper.textContent = "Escrows cash (BUY) or shares (SELL) until live market ticks reach your limit price.";
    }
    updateOrderEstimate();
  }

  function updateOrderEstimate() {
    const price = Number($("orderPriceInput").value || 0);
    const qty = Math.max(0, Math.floor(Number($("orderQtyInput").value || 0)));
    $("orderEstimatedTotal").textContent = fmtInr(price * qty);
  }

  function applyQuickPositionSize(pct) {
    const stock = getSelectedStock();
    if (!stock || !state.portfolio) return;
    const unitPrice = state.orderType === "LIMIT"
      ? Number($("orderPriceInput").value || stock.price)
      : Number(stock.price);
    if (unitPrice <= 0) return;

    const budget = Number(state.portfolio.cash || 0) * pct;
    const shares = Math.max(1, Math.floor(budget / unitPrice));
    $("orderQtyInput").value = String(shares);
    updateOrderEstimate();
  }

  async function submitTrade(side) {
    if (!isTrader()) {
      showToast("Sign in with a Trader account to place orders.", true);
      return;
    }

    const stock = getSelectedStock();
    if (!stock) return;

    const quantity = Math.floor(Number($("orderQtyInput").value || 0));
    if (quantity < 1) {
      showToast("Quantity must be at least 1 share.", true);
      return;
    }

    const payload = {
      symbol: stock.symbol,
      side,
      type: state.orderType,
      quantity
    };

    if (state.orderType === "LIMIT") {
      const limitPrice = Number($("orderPriceInput").value || 0);
      if (limitPrice <= 0) {
        showToast("Enter a valid positive limit price.", true);
        return;
      }
      payload.limitPrice = limitPrice;
    }

    try {
      const order = await api("/api/orders", {
        method: "POST",
        body: JSON.stringify(payload)
      });

      const fillOrLimitPx = order.executedPrice ?? order.limitPrice;
      const statusMsg =
        order.status === "FILLED"
          ? `${side} ${order.quantity} ${order.symbol} filled @ ${fmtInr(fillOrLimitPx)}`
          : `Limit ${side} placed: ${order.quantity} ${order.symbol} @ ${fmtInr(order.limitPrice)} (PENDING)`;

      showToast(statusMsg);
      pushNotification(
        order.status === "FILLED" ? `${side} Order Filled` : `Limit ${side} Working`,
        statusMsg,
        symbolInitials(order.symbol),
        symbolColor(order.symbol)
      );

      await loadAllWorkspaceData();
      drawMainChart();
    } catch (err) {
      showToast(err.message, true);
    }
  }

  async function cancelPendingOrder(orderId) {
    try {
      const res = await api(`/api/orders/${orderId}/cancel`, { method: "POST" });
      showToast(`Cancelled Limit #${res.id} (${res.symbol}) — Escrow released`);
      pushNotification("Limit Order Cancelled", `Escrow refunded for ${res.symbol} (#${res.id})`, "↺", "#F59E0B");
      await loadAllWorkspaceData();
      drawMainChart();
    } catch (err) {
      showToast(err.message, true);
    }
  }

  /* ========================================================================
     VIEW 2: FULL MARKET & OPEN POSITIONS TABLES
     ======================================================================== */

  function renderFullMarketTable() {
    const tbody = $("fullMarketTableBody");
    if (!tbody) return;
    const rows = getFilteredStocks();

    tbody.innerHTML = rows
      .map((s) => {
        const up = Number(s.changePercent) >= 0;
        const pts = ensureSparkSeries(s);
        return `
          <tr class="tabular">
            <td>
              <div class="trend-asset-cell">
                <span class="asset-circle-icon" style="background:${symbolColor(s.symbol)}">${escapeHtml(symbolInitials(s.symbol))}</span>
                <div class="trend-asset-meta">
                  <strong>${escapeHtml(s.symbol)}</strong>
                  <small>${escapeHtml(s.name)}</small>
                </div>
              </div>
            </td>
            <td><span class="sector-tag">${escapeHtml(s.sector)}</span></td>
            <td>${renderSparklineSvg(pts, up, `mkt_${s.symbol}`)}</td>
            <td class="num"><strong>${fmtInr(s.price)}</strong></td>
            <td class="num">${fmtInr(s.previousClose)}</td>
            <td class="num"><span class="badge-pill ${up ? "positive" : "negative"}">${fmtPct(s.changePercent)}</span></td>
            <td class="num">
              <button type="button" class="table-btn-sm" data-trade-jump="${escapeHtml(s.symbol)}">Trade →</button>
            </td>
          </tr>
        `;
      })
      .join("");
  }

  function renderOpenPositionsTable() {
    const tbody = $("openPositionsTableBody");
    if (!tbody) return;
    const holdings = state.portfolio?.holdings || [];
    $("positionsSummaryTag").textContent = `${holdings.length} Active Holding${holdings.length === 1 ? "" : "s"}`;

    if (!holdings.length) {
      tbody.innerHTML = `<tr><td colspan="8" class="muted-xs">No open equity holdings yet. Buy any stock from the Dashboard to track live mark-to-market P/L.</td></tr>`;
      return;
    }

    tbody.innerHTML = holdings
      .map((pos) => {
        const up = Number(pos.unrealizedPnl) >= 0;
        return `
          <tr class="tabular">
            <td>
              <div class="trend-asset-cell">
                <span class="coin-avatar-sm" style="background:${symbolColor(pos.symbol)}">${escapeHtml(symbolInitials(pos.symbol))}</span>
                <strong>${escapeHtml(pos.symbol)}</strong>
              </div>
            </td>
            <td class="num">${pos.quantity}</td>
            <td class="num">${pos.availableQuantity} / ${pos.reservedQuantity}</td>
            <td class="num">${fmtInr(pos.avgBuyPrice)}</td>
            <td class="num">${fmtInr(pos.currentPrice)}</td>
            <td class="num"><strong>${fmtInr(pos.marketValue)}</strong></td>
            <td class="num">
              <span class="badge-pill ${up ? "positive" : "negative"}">
                ${fmtInr(pos.unrealizedPnl)} (${fmtPct(pos.unrealizedPnlPercent)})
              </span>
            </td>
            <td class="num">
              ${
                pos.availableQuantity > 0
                  ? `<button type="button" class="table-btn-sm danger" data-quick-close="${escapeHtml(pos.symbol)}" data-close-qty="${pos.availableQuantity}">Market Close</button>`
                  : `<span class="muted-xs">Escrowed</span>`
              }
            </td>
          </tr>
        `;
      })
      .join("");
  }

  async function quickClosePosition(symbol, qty) {
    try {
      const order = await api("/api/orders", {
        method: "POST",
        body: JSON.stringify({
          symbol,
          side: "SELL",
          type: "MARKET",
          quantity: Number(qty)
        })
      });
      showToast(`Closed ${order.quantity} ${order.symbol} @ ${fmtInr(order.executedPrice)}`);
      await loadAllWorkspaceData();
    } catch (err) {
      showToast(err.message, true);
    }
  }

  /* ========================================================================
     VIEW 3: MY WALLETS + MONEY FLOW BAR CHART + CONVERSION
     (Aligned 100% with WalletResponse & LedgerEntryResponse DTOs)
     ======================================================================== */

  function renderWalletView() {
    const w = state.wallet;
    if (!w) return;

    const avail = Number(w.balance ?? 0);
    const reserved = Number(w.reservedCash ?? 0);
    const totalCash = Number(w.totalCash ?? avail + reserved);
    const entries = w.ledger || [];
    const equityVal = Number(state.portfolio?.holdingsValue ?? 0);

    // When ledgerConsistent is verified on the server, SUM(ledger_entries.amount) === totalCash
    const ledgerSum = w.ledgerConsistent
      ? totalCash
      : entries.reduce((acc, e) => acc + Number(e.amount || 0), 0);

    $("walletTotalCash").textContent = fmtInr(totalCash);
    $("walletDateLabel").textContent = new Date().toLocaleDateString("en-IN", {
      day: "2-digit",
      month: "long",
      year: "numeric"
    }).toUpperCase();

    const badge = $("walletLedgerBadge");
    if (w.ledgerConsistent) {
      badge.className = "badge-pill positive";
      badge.textContent = "✓ Ledger Verified";
    } else {
      badge.className = "badge-pill negative";
      badge.textContent = "⚠ Checksum Mismatch";
    }

    $("eqAvail").textContent = fmtInr(avail);
    $("eqReserved").textContent = fmtInr(reserved);
    $("eqLedgerSum").textContent = fmtInr(ledgerSum);

    $("allocAvailCash").textContent = fmtInr(avail);
    $("allocReservedCash").textContent = fmtInr(reserved);
    $("allocEquityVal").textContent = fmtInr(equityVal);

    $("ledgerCountTag").textContent = `${entries.length} Entries`;

    const feed = $("walletActivityFeed");
    if (!entries.length) {
      feed.innerHTML = `<p class="muted-xs">No ledger entries recorded.</p>`;
    } else {
      feed.innerHTML = entries
        .slice(0, 14)
        .map((e) => {
          const amt = Number(e.amount || 0);
          const plus = amt >= 0;
          const color = plus ? "#10B981" : "#6366F1";
          const icon = plus ? "↙" : "↗";
          return `
            <div class="activity-row tabular">
              <div class="activity-left">
                <span class="coin-avatar-sm" style="background:${color}">${icon}</span>
                <div>
                  <strong>${escapeHtml(String(e.type || "").replace(/_/g, " "))}</strong>
                  <small>${escapeHtml(e.description || "Ledger settlement")} · ${fmtDateShort(e.createdAt)}</small>
                </div>
              </div>
              <span class="activity-amt ${plus ? "plus" : "minus"}">${plus ? "+" : ""}${fmtInr(amt)}</span>
            </div>
          `;
        })
        .join("");
    }

    if (state.activeView === "wallet") {
      drawMoneyFlowChart();
    }
  }

  function drawMoneyFlowChart() {
    const canvas = $("moneyFlowCanvas");
    if (!canvas || canvas.offsetParent === null) return;

    const rect = canvas.parentElement.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;
    const width = Math.max(280, rect.width);
    const height = Math.max(190, rect.height);

    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    const ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);
    ctx.clearRect(0, 0, width, height);

    const entries = (state.wallet?.ledger || []).slice(0, 14).reverse();
    const labels = ["T-6", "T-5", "T-4", "T-3", "T-2", "T-1", "Now"];
    const buckets = labels.map((lbl) => ({ label: lbl, inflow: 0, outflow: 0 }));

    if (entries.length) {
      entries.forEach((e, idx) => {
        const bIdx = Math.min(buckets.length - 1, Math.floor((idx / Math.max(1, entries.length)) * buckets.length));
        const amt = Number(e.amount || 0);
        if (amt >= 0) buckets[bIdx].inflow += amt;
        else buckets[bIdx].outflow += Math.abs(amt);
        buckets[bIdx].label = fmtTimeShort(e.createdAt).slice(0, 5) || buckets[bIdx].label;
      });
    } else {
      buckets[6].inflow = 1000000;
    }

    const maxVal = Math.max(10000, ...buckets.flatMap((b) => [b.inflow, b.outflow]));
    const isDark = state.theme === "dark";
    const barBlack = isDark ? "#F8FAFC" : "#111318";
    const barGray = isDark ? "#334155" : "#CBD5E1";
    const gridCol = isDark ? "rgba(148,163,184,0.12)" : "#EEF1F6";
    const textCol = isDark ? "#94A3B8" : "#8A94A6";

    const padTop = 34;
    const padBottom = 26;
    const padX = 20;
    const plotW = width - padX * 2;
    const plotH = height - padTop - padBottom;

    for (let g = 0; g <= 3; g++) {
      const y = padTop + (g / 3) * plotH;
      ctx.save();
      ctx.setLineDash([4, 4]);
      ctx.strokeStyle = gridCol;
      ctx.beginPath();
      ctx.moveTo(padX, y);
      ctx.lineTo(padX + plotW, y);
      ctx.stroke();
      ctx.restore();
    }

    const groupW = plotW / buckets.length;
    const barW = Math.min(16, Math.max(9, groupW * 0.22));
    let peakVal = -1;
    let peakX = 0;
    let peakY = 0;

    ctx.font = "600 11px 'Plus Jakarta Sans', sans-serif";
    ctx.textAlign = "center";

    buckets.forEach((b, idx) => {
      const centerX = padX + idx * groupW + groupW / 2;

      const inH = Math.max(6, (b.inflow / maxVal) * plotH);
      const outH = Math.max(6, (b.outflow / maxVal) * plotH);

      const inY = padTop + plotH - inH;
      const outY = padTop + plotH - outH;

      ctx.fillStyle = barBlack;
      roundRect(ctx, centerX - barW - 3, inY, barW, inH, 6);
      ctx.fill();

      ctx.fillStyle = barGray;
      roundRect(ctx, centerX + 3, outY, barW, outH, 6);
      ctx.fill();

      const domVal = Math.max(b.inflow, b.outflow);
      if (domVal > peakVal) {
        peakVal = domVal;
        peakX = b.inflow >= b.outflow ? centerX - barW / 2 - 3 : centerX + barW / 2 + 3;
        peakY = Math.min(inY, outY);
      }

      ctx.fillStyle = textCol;
      ctx.fillText(b.label, centerX, height - 7);
    });

    if (peakVal > 0) {
      const tag = fmtCompactInr(peakVal);
      ctx.font = "700 11px 'Plus Jakarta Sans', sans-serif";
      const tw = ctx.measureText(tag).width + 14;
      const bx = Math.min(Math.max(padX, peakX - tw / 2), width - padX - tw);
      const by = Math.max(4, peakY - 28);
      ctx.fillStyle = barBlack;
      roundRect(ctx, bx, by, tw, 22, 6);
      ctx.fill();
      ctx.fillStyle = isDark ? "#0F172A" : "#FFFFFF";
      ctx.fillText(tag, bx + tw / 2, by + 14.5);
    }
  }

  function updateConversionOutput() {
    const inr = Math.max(0, Number($("convInrInput")?.value || 0));
    const sym = $("convSymbolSelect")?.value || state.selectedSymbol;
    const stock = state.stocks.find((s) => s.symbol === sym) || getSelectedStock();
    if (!stock || !$("convSharesOutput")) return;

    const p = Number(stock.price);
    const wholeShares = p > 0 ? Math.floor(inr / p) : 0;
    const remainder = inr - wholeShares * p;
    $("convSharesOutput").value = `${wholeShares} shares (@ ₹${p.toFixed(2)})`;
    $("convRemainderNote").textContent = `Cash remainder: ${fmtInr(remainder)}`;
  }

  /* ========================================================================
     VIEW 4: ORDERS BLOTTER & CSV EXPORT
     (Aligned 100% with OrderResponse DTO)
     ======================================================================== */

  function buildOrderFilterQuery() {
    const params = new URLSearchParams();
    const sym = $("fltSymbol")?.value.trim();
    const side = $("fltSide")?.value;
    const type = $("fltType")?.value;
    const status = $("fltStatus")?.value;

    if (sym) params.set("symbol", sym.toUpperCase());
    if (side) params.set("side", side);
    if (type) params.set("type", type);
    if (status) params.set("status", status);

    const qs = params.toString();
    return qs ? `?${qs}` : "";
  }

  function renderOrdersTable() {
    const tbody = $("ordersTableBody");
    if (!tbody) return;

    if (!state.orders.length) {
      tbody.innerHTML = `<tr><td colspan="10" class="muted-xs">No orders match the active filter.</td></tr>`;
      return;
    }

    tbody.innerHTML = state.orders
      .map((o) => {
        const unitPrice = Number(o.executedPrice ?? o.limitPrice ?? 0);
        const totalVal = Number(o.totalValue ?? unitPrice * Number(o.quantity || 0));
        const statusCls =
          o.status === "FILLED" ? "positive" : o.status === "PENDING" ? "neutral" : "negative";

        return `
          <tr class="tabular">
            <td>#${o.id}</td>
            <td>${fmtDateShort(o.createdAt)}</td>
            <td><strong>${escapeHtml(o.symbol)}</strong></td>
            <td><span class="badge-pill ${o.side === "BUY" ? "positive" : "negative"}">${escapeHtml(o.side)}</span></td>
            <td>${escapeHtml(o.type)}</td>
            <td class="num">${o.quantity}</td>
            <td class="num">${fmtInr(unitPrice)}</td>
            <td class="num"><strong>${fmtInr(totalVal)}</strong></td>
            <td><span class="badge-pill ${statusCls}">${escapeHtml(o.status)}</span></td>
            <td class="num">
              ${
                o.status === "PENDING"
                  ? `<button type="button" class="table-btn-sm danger" data-cancel-order="${o.id}">Cancel</button>`
                  : `<span class="muted-xs">—</span>`
              }
            </td>
          </tr>
        `;
      })
      .join("");
  }

  async function downloadOrdersCsv() {
    if (!isTrader()) return;
    try {
      const resp = await fetch(`/api/orders/export.csv${buildOrderFilterQuery()}`, {
        headers: state.token ? { Authorization: `Bearer ${state.token}` } : {}
      });
      if (!resp.ok) throw new Error("CSV export failed");
      const blob = await resp.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `tradesim-orders-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      showToast("Downloaded orders audit CSV");
    } catch (err) {
      showToast(err.message, true);
    }
  }

  /* ========================================================================
     VIEW 5: LEADERBOARD (Aligned 100% with LeaderboardEntry DTO)
     ======================================================================== */

  function renderLeaderboardView() {
    const podium = $("leaderboardPodium");
    const tbody = $("leaderboardTableBody");
    if (!podium || !tbody) return;

    const rows = state.leaderboard || [];
    podium.innerHTML = rows
      .slice(0, 3)
      .map((r) => {
        const retPct = Number(r.totalPnlPercent ?? 0);
        const medal = r.rank === 1 ? "🥇 Rank #1" : r.rank === 2 ? "🥈 Rank #2" : "🥉 Rank #3";
        return `
          <article class="podium-card tabular">
            <div>
              <span class="podium-rank">${medal}</span>
              <div class="podium-name">${escapeHtml(r.username)}</div>
              <span class="badge-pill ${retPct >= 0 ? "positive" : "negative"}">${fmtPct(retPct)}</span>
            </div>
            <div class="podium-worth">${fmtCompactInr(r.totalValue)}</div>
          </article>
        `;
      })
      .join("");

    tbody.innerHTML = rows
      .map((r) => {
        const retPct = Number(r.totalPnlPercent ?? 0);
        const isMe = state.user && r.username === state.user.username;
        return `
          <tr class="tabular">
            <td><strong>#${r.rank}</strong></td>
            <td>
              <div class="trend-asset-cell">
                <span class="coin-avatar-sm" style="background:${symbolColor(r.username)}">${escapeHtml(r.username.slice(0, 1).toUpperCase())}</span>
                <strong>${escapeHtml(r.username)}${isMe ? " (You)" : ""}</strong>
              </div>
            </td>
            <td class="num">${fmtInr(r.cashValue)}</td>
            <td class="num">${fmtInr(r.holdingsValue)}</td>
            <td class="num"><strong>${fmtInr(r.totalValue)}</strong></td>
            <td class="num"><span class="badge-pill ${retPct >= 0 ? "positive" : "negative"}">${fmtPct(retPct)}</span></td>
          </tr>
        `;
      })
      .join("");
  }

  /* ========================================================================
     VIEW 6: EXCHANGE ADMIN CONSOLE (Aligned 100% with AdminUserSummary DTO)
     ======================================================================== */

  function renderAdminView() {
    $("adminStockCount").textContent = String(state.stocks.length);

    $("adminStocksTableBody").innerHTML = state.stocks
      .map(
        (s) => `
        <tr class="tabular">
          <td><strong>${escapeHtml(s.symbol)}</strong><br/><small class="muted-xs">${escapeHtml(s.name)}</small></td>
          <td>${escapeHtml(s.sector)}</td>
          <td class="num">${fmtInr(s.price)}</td>
          <td class="num">
            <button type="button" class="table-btn-sm danger" data-admin-delist="${escapeHtml(s.symbol)}">Delist &amp; Settle</button>
          </td>
        </tr>
      `
      )
      .join("");

    $("adminUsersTableBody").innerHTML = (state.adminUsers || [])
      .map(
        (u) => {
          const isAdminRow = u.role === "ADMIN";
          return `
        <tr class="tabular">
          <td>#${u.id}</td>
          <td><strong>${escapeHtml(u.username)}</strong></td>
          <td><span class="sector-tag">${escapeHtml(u.role)}</span></td>
          <td class="num">${isAdminRow ? "—" : fmtInr(u.cash)}</td>
          <td class="num">${isAdminRow ? "—" : fmtInr(u.holdingsValue)}</td>
          <td class="num"><strong>${isAdminRow ? "—" : fmtInr(u.totalValue)}</strong></td>
          <td>
            ${
              isAdminRow
                ? `<span class="muted-xs">N/A</span>`
                : `<span class="badge-pill ${u.ledgerConsistent ? "positive" : "negative"}">${
                    u.ledgerConsistent ? "✓ Verified" : "⚠ Drift"
                  }</span>`
            }
          </td>
          <td class="num">
            ${
              isAdminRow
                ? `<span class="muted-xs">System Admin</span>`
                : `<button type="button" class="table-btn-sm" data-open-deposit="${u.id}" data-deposit-user="${escapeHtml(u.username)}">+ Credit</button>
                   <button type="button" class="table-btn-sm danger" data-reset-user="${u.id}" data-reset-name="${escapeHtml(u.username)}">Reset ₹10L</button>`
            }
          </td>
        </tr>
      `;
        }
      )
      .join("");
  }

  /* ========================================================================
     NOTIFICATIONS POPOVER
     ======================================================================== */

  function seedStarterNotifications() {
    if (state.notifications.length > 0) return;
    if (isTrader()) {
      pushNotification(
        "Custody Wallet Active",
        `${fmtInr(state.portfolio?.cash ?? 1000000)} available in your ACID ledger`,
        "₹",
        "#10B981"
      );
    } else {
      pushNotification(
        "Exchange Admin Session",
        `Managing ${state.stocks.length} listed NSE equities`,
        "A",
        "#6366F1"
      );
    }
    const top = state.stocks[0];
    if (top) {
      pushNotification(
        `${top.symbol} Live Feed Active`,
        `Streaming real-time 1.5s NSE ticks (${fmtInr(top.price)})`,
        symbolInitials(top.symbol),
        symbolColor(top.symbol)
      );
    }
  }

  function renderNotifications() {
    const dot = $("notifUnreadDot");
    if (dot) dot.hidden = !state.unreadNotifs;

    const list = $("notifList");
    if (!list) return;

    if (!state.notifications.length) {
      list.innerHTML = `<p class="muted-xs">No notifications yet.</p>`;
      return;
    }

    list.innerHTML = state.notifications
      .map(
        (n) => `
        <div class="notif-item">
          <span class="notif-icon-badge" style="background:${n.color};color:#fff">${escapeHtml(n.symbol)}</span>
          <div class="notif-body">
            <strong>${escapeHtml(n.title)}</strong>
            <p>${escapeHtml(n.detail)} · ${escapeHtml(n.time)}</p>
          </div>
        </div>
      `
      )
      .join("");
  }

  /* ========================================================================
     EVENT WIRING & BOOTSTRAP
     ======================================================================== */

  function bindEvents() {
    $("tabLogin").addEventListener("click", () => setAuthMode("login"));
    $("tabRegister").addEventListener("click", () => setAuthMode("register"));
    $("authForm").addEventListener("submit", handleAuthSubmit);
    $("instantTraderBtn").addEventListener("click", launchInstantTraderDemo);
    $("instantAdminBtn").addEventListener("click", launchInstantAdminDemo);
    $("logoutBtn").addEventListener("click", handleLogout);

    document.querySelectorAll(".nav-item[data-view]").forEach((btn) => {
      btn.addEventListener("click", () => switchView(btn.dataset.view));
    });

    document.body.addEventListener("click", (ev) => {
      const gotoBtn = ev.target.closest("[data-goto-view]");
      if (gotoBtn) {
        switchView(gotoBtn.dataset.gotoView);
        return;
      }

      const selRow = ev.target.closest("[data-select-symbol]");
      if (selRow && !ev.target.closest("button")) {
        selectSymbol(selRow.dataset.selectSymbol, false);
        return;
      }

      const tradeJump = ev.target.closest("[data-trade-jump]");
      if (tradeJump) {
        selectSymbol(tradeJump.dataset.tradeJump, true);
        return;
      }

      const snapLimit = ev.target.closest("[data-snap-limit]");
      if (snapLimit) {
        setOrderType("LIMIT");
        $("orderPriceInput").value = Number(snapLimit.dataset.snapLimit).toFixed(2);
        updateOrderEstimate();
        showToast(`Limit price set to ₹${Number(snapLimit.dataset.snapLimit).toFixed(2)}`);
        return;
      }

      const cancelBtn = ev.target.closest("[data-cancel-order]");
      if (cancelBtn) {
        cancelPendingOrder(cancelBtn.dataset.cancelOrder);
        return;
      }

      const closeBtn = ev.target.closest("[data-quick-close]");
      if (closeBtn) {
        quickClosePosition(closeBtn.dataset.quickClose, closeBtn.dataset.closeQty);
        return;
      }

      const delistBtn = ev.target.closest("[data-admin-delist]");
      if (delistBtn) {
        const sym = delistBtn.dataset.adminDelist;
        api(`/api/admin/stocks/${encodeURIComponent(sym)}`, { method: "DELETE" })
          .then(() => {
            showToast(`Delisted ${sym} and settled all open shareholder positions in cash`);
            return loadAllWorkspaceData();
          })
          .catch((err) => showToast(err.message, true));
        return;
      }

      const depBtn = ev.target.closest("[data-open-deposit]");
      if (depBtn) {
        state.depositTargetUser = {
          id: depBtn.dataset.openDeposit,
          username: depBtn.dataset.depositUser
        };
        $("depositTargetLabel").textContent = `Crediting trader @${state.depositTargetUser.username} (Account #${state.depositTargetUser.id})`;
        $("depositModal").hidden = false;
        return;
      }

      const resetBtn = ev.target.closest("[data-reset-user]");
      if (resetBtn) {
        const uid = resetBtn.dataset.resetUser;
        const uname = resetBtn.dataset.resetName;
        api(`/api/admin/users/${uid}/reset`, { method: "POST" })
          .then(() => {
            showToast(`Reset @${uname} back to ₹10,00,000.00`);
            return loadAllWorkspaceData();
          })
          .catch((err) => showToast(err.message, true));
        return;
      }

      const sectorBtn = ev.target.closest("[data-sector]");
      if (sectorBtn) {
        state.selectedSector = sectorBtn.dataset.sector;
        renderSectorPills();
        renderDashboardMarketTrend(new Map());
        renderFullMarketTable();
        return;
      }

      if (!ev.target.closest(".notif-anchor")) {
        $("notifPopover").hidden = true;
      }
    });

    $("globalSearchInput").addEventListener("input", (e) => {
      state.searchQuery = e.target.value;
      renderDashboardMarketTrend(new Map());
      renderFullMarketTable();
    });
    $("sectorFilterToggleBtn").addEventListener("click", () => {
      const bar = $("sectorFilterBar");
      bar.hidden = !bar.hidden;
    });

    $("notifBellBtn").addEventListener("click", () => {
      const pop = $("notifPopover");
      pop.hidden = !pop.hidden;
      if (!pop.hidden) {
        state.unreadNotifs = false;
        renderNotifications();
      }
    });
    $("markAllReadBtn").addEventListener("click", () => {
      state.unreadNotifs = false;
      renderNotifications();
    });
    $("seeAllActivityBtn").addEventListener("click", () => {
      $("notifPopover").hidden = true;
      switchView(isTrader() ? "wallet" : "admin");
    });

    $("themeToggleBtn").addEventListener("click", () => {
      applyTheme(state.theme === "light" ? "dark" : "light");
    });
    $("helpModalBtn").addEventListener("click", () => {
      $("helpModal").hidden = false;
    });
    $("closeHelpModalBtn").addEventListener("click", () => {
      $("helpModal").hidden = true;
    });

    document.querySelectorAll(".pill-tab[data-chart-mode]").forEach((btn) => {
      btn.addEventListener("click", () => setChartMode(btn.dataset.chartMode));
    });
    $("symbolSelectDropdown").addEventListener("change", (e) => {
      selectSymbol(e.target.value, false);
    });

    const mainCanvas = $("mainPriceCanvas");
    mainCanvas.addEventListener("mousemove", (ev) => {
      if (state.chartMode !== "line" || !state.chartHistory.length) return;
      const rect = mainCanvas.getBoundingClientRect();
      const relX = ev.clientX - rect.left - 14;
      const plotW = Math.max(1, rect.width - 82);
      const ratio = Math.min(1, Math.max(0, relX / plotW));
      state.hoverPointIdx = Math.round(ratio * (state.chartHistory.length - 1));
      drawMainChart();
    });
    mainCanvas.addEventListener("mouseleave", () => {
      state.hoverPointIdx = null;
      drawMainChart();
    });
    window.addEventListener("resize", () => {
      drawMainChart();
      drawMoneyFlowChart();
    });

    $("typeMarketBtn").addEventListener("click", () => setOrderType("MARKET"));
    $("typeLimitBtn").addEventListener("click", () => setOrderType("LIMIT"));
    $("orderPriceInput").addEventListener("input", updateOrderEstimate);
    $("orderQtyInput").addEventListener("input", updateOrderEstimate);

    document.querySelectorAll("[data-size-pct]").forEach((chip) => {
      chip.addEventListener("click", () => applyQuickPositionSize(Number(chip.dataset.sizePct)));
    });
    document.querySelectorAll("[data-snap]").forEach((chip) => {
      chip.addEventListener("click", () => {
        const stock = getSelectedStock();
        if (!stock) return;
        const delta = Number(chip.dataset.snap);
        const snapped = Number(stock.price) * (1 + delta);
        $("orderPriceInput").value = snapped.toFixed(2);
        updateOrderEstimate();
      });
    });

    $("buyActionBtn").addEventListener("click", () => submitTrade("BUY"));
    $("sellActionBtn").addEventListener("click", () => submitTrade("SELL"));

    $("convInrInput").addEventListener("input", updateConversionOutput);
    $("convSymbolSelect").addEventListener("change", updateConversionOutput);
    $("applyConvToTicketBtn").addEventListener("click", () => {
      const sym = $("convSymbolSelect").value;
      const inr = Number($("convInrInput").value || 0);
      const stock = state.stocks.find((s) => s.symbol === sym);
      if (stock && Number(stock.price) > 0) {
        const sh = Math.max(1, Math.floor(inr / Number(stock.price)));
        $("orderQtyInput").value = String(sh);
        selectSymbol(sym, true);
      }
    });
    $("walletExportCsvBtn").addEventListener("click", downloadOrdersCsv);
    $("walletRefreshAuditBtn").addEventListener("click", async () => {
      if (!isTrader()) return;
      state.wallet = await api("/api/wallet");
      renderWalletView();
      showToast(state.wallet.ledgerConsistent ? "Ledger Checksum Verified ✓" : "Ledger drift detected", !state.wallet.ledgerConsistent);
    });

    $("orderFilterForm").addEventListener("submit", (e) => {
      e.preventDefault();
      refreshOrdersOnly();
    });
    $("resetOrderFilterBtn").addEventListener("click", () => {
      $("orderFilterForm").reset();
      refreshOrdersOnly();
    });
    $("exportCsvBtn").addEventListener("click", downloadOrdersCsv);

    $("adminAddStockForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        const body = {
          symbol: $("admSymbol").value.trim().toUpperCase(),
          name: $("admName").value.trim(),
          sector: $("admSector").value.trim(),
          price: Number($("admPrice").value)
        };
        await api("/api/admin/stocks", { method: "POST", body: JSON.stringify(body) });
        $("adminAddStockForm").reset();
        showToast(`Listed ${body.symbol} on NSE exchange`);
        await loadAllWorkspaceData();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    $("closeDepositModalBtn").addEventListener("click", () => ($("depositModal").hidden = true));
    $("cancelDepositBtn").addEventListener("click", () => ($("depositModal").hidden = true));
    $("adminDepositForm").addEventListener("submit", async (e) => {
      e.preventDefault();
      if (!state.depositTargetUser) return;
      try {
        await api(`/api/admin/users/${state.depositTargetUser.id}/deposit`, {
          method: "POST",
          body: JSON.stringify({
            amount: Number($("depositAmountInput").value)
          })
        });
        $("depositModal").hidden = true;
        showToast(`Credited @${state.depositTargetUser.username}`);
        await loadAllWorkspaceData();
      } catch (err) {
        showToast(err.message, true);
      }
    });

    document.addEventListener("keydown", (ev) => {
      const tag = (ev.target && ev.target.tagName) || "";
      const typing = tag === "INPUT" || tag === "SELECT" || tag === "TEXTAREA";

      if (ev.key === "Escape") {
        $("notifPopover").hidden = true;
        $("depositModal").hidden = true;
        $("helpModal").hidden = true;
        return;
      }

      if (typing || !state.user) return;

      if (ev.key === "/") {
        ev.preventDefault();
        $("globalSearchInput").focus();
      } else if (ev.key === "1") {
        switchView("dashboard");
      } else if (ev.key === "2") {
        switchView("market");
      } else if (ev.key === "3" && isTrader()) {
        switchView("wallet");
      } else if (ev.key === "4" && isTrader()) {
        switchView("orders");
      } else if (ev.key === "5") {
        switchView("leaderboard");
      }
    });
  }

  async function init() {
    applyTheme(state.theme);
    bindEvents();

    try {
      const stocks = await api("/api/stocks");
      onMarketQuotesUpdate(stocks);
    } catch (_) {}
    connectMarketStream();

    if (state.token) {
      try {
        const me = await api("/api/auth/me");
        state.user = me;
        await enterAuthenticatedShell();
        return;
      } catch (_) {
        localStorage.removeItem("tradesim_token");
        state.token = null;
        state.user = null;
      }
    }

    $("authScreen").hidden = false;
    $("appShell").hidden = true;
  }

  init();
})();
