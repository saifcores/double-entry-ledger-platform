(() => {
  "use strict";

  const QUICK_LOGIN_ACCOUNTS = [
    { email: "user@ledger.local", label: "Demo User", note: "USER · USD" },
    { email: "alice@ledger.local", label: "Alice", note: "USER · USD" },
    { email: "bob@ledger.local", label: "Bob", note: "USER · EUR" },
    { email: "merchant@ledger.local", label: "Merchant", note: "MERCHANT · USD" },
    { email: "ops@ledger.local", label: "Operations", note: "OPERATIONS · USD" },
    { email: "admin@ledger.local", label: "Admin", note: "ADMIN · USD" },
  ];
  const QUICK_LOGIN_PASSWORD = "ChangeMe123!";
  const PRIVILEGED_ROLES = ["ADMIN", "OPERATIONS", "COMPLIANCE"];

  const state = {
    token: sessionStorage.getItem("ledger_demo_token") || null,
    user: null,
    directory: [],
  };

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  function decodeJwt(token) {
    try {
      const payload = token.split(".")[1];
      const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
      return JSON.parse(decodeURIComponent(escape(json)));
    } catch (e) {
      return null;
    }
  }

  function fmtMoney(minor, currency) {
    return (Number(minor) / 100).toLocaleString(undefined, {
      style: "currency",
      currency: currency || "USD",
    });
  }

  function fmtWhen(iso) {
    if (!iso) return "—";
    try {
      return new Date(iso).toLocaleString(undefined, {
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch (e) {
      return iso;
    }
  }

  function toast(message, isError) {
    const el = $("#toast");
    el.textContent = message;
    el.classList.toggle("error", !!isError);
    el.classList.remove("hidden");
    requestAnimationFrame(() => el.classList.add("show"));
    clearTimeout(toast._t);
    toast._t = setTimeout(() => {
      el.classList.remove("show");
      setTimeout(() => el.classList.add("hidden"), 220);
    }, 3800);
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));
  }

  function logCall(method, path, status, ok, reqBody, resBody, ms) {
    const container = $("#call-log");
    const entry = document.createElement("div");
    entry.className = "log-entry";
    const reqPreview = reqBody ? JSON.stringify(reqBody) : "";
    const resPreview =
      typeof resBody === "string" ? resBody : JSON.stringify(resBody ?? null);
    entry.innerHTML = `
      <div class="line1">
        <span class="method">${method}</span>
        <span>${escapeHtml(path)}</span>
        <span class="${ok ? "status-ok" : "status-err"}">${status}</span>
        <span>${ms}ms</span>
      </div>
      <pre>${reqPreview ? "→ " + escapeHtml(reqPreview) + "\n" : ""}← ${escapeHtml(resPreview)}</pre>
    `;
    container.prepend(entry);
    while (container.children.length > 40) container.removeChild(container.lastChild);
  }

  async function api(method, path, body, { auth = true } = {}) {
    const started = performance.now();
    const headers = { Accept: "application/json" };
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (auth && state.token) headers.Authorization = "Bearer " + state.token;
    const res = await fetch(path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    const ms = Math.round(performance.now() - started);
    let data = null;
    const text = await res.text();
    if (text) {
      try {
        data = JSON.parse(text);
      } catch (e) {
        data = text;
      }
    }
    logCall(method, path, res.status, res.ok, body, data, ms);
    if (!res.ok) {
      const detail =
        (data && (data.detail || data.title || data.message)) || res.statusText;
      const err = new Error(detail);
      err.status = res.status;
      err.body = data;
      throw err;
    }
    return data;
  }

  function isPrivileged() {
    return (state.user?.roles || []).some((r) => PRIVILEGED_ROLES.includes(r));
  }

  function renderQuickLogins() {
    const container = $("#quick-login-list");
    container.innerHTML = "";
    QUICK_LOGIN_ACCOUNTS.forEach((acc) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "quick-login-btn";
      btn.innerHTML = `
        <span>
          <span class="label">${escapeHtml(acc.label)}</span>
          <span class="role-badge">${escapeHtml(acc.email)}</span>
        </span>
        <span class="role-badge">${escapeHtml(acc.note)}</span>`;
      btn.addEventListener("click", () => doLogin(acc.email, QUICK_LOGIN_PASSWORD));
      container.appendChild(btn);
    });
  }

  async function doLogin(email, password) {
    try {
      const res = await api(
        "POST",
        "/api/v1/auth/login",
        { email, password },
        { auth: false }
      );
      setSession(res.accessToken);
      toast("Signed in as " + email);
    } catch (e) {
      toast("Login failed: " + e.message, true);
    }
  }

  async function doRegister(email, password, preferredCurrency) {
    try {
      const res = await api(
        "POST",
        "/api/v1/auth/register",
        { email, password, preferredCurrency },
        { auth: false }
      );
      setSession(res.accessToken);
      toast("Account created for " + email);
    } catch (e) {
      toast("Registration failed: " + e.message, true);
    }
  }

  function setSession(token) {
    state.token = token;
    sessionStorage.setItem("ledger_demo_token", token);
    const claims = decodeJwt(token);
    if (!claims) {
      logout();
      toast("Invalid token payload", true);
      return;
    }
    state.user = {
      id: String(claims.sub),
      email: claims.email,
      roles: claims.roles || [],
    };
    enterApp();
  }

  function logout() {
    state.token = null;
    state.user = null;
    sessionStorage.removeItem("ledger_demo_token");
    $("#app-view").classList.add("hidden");
    $("#auth-view").classList.remove("hidden");
    $("#session-box").classList.add("hidden");
  }

  async function enterApp() {
    $("#auth-view").classList.add("hidden");
    $("#app-view").classList.remove("hidden");
    const box = $("#session-box");
    box.classList.remove("hidden");
    $("#session-email").textContent = state.user.email;
    $("#session-roles").textContent = (state.user.roles || []).join(", ");

    const privileged = isPrivileged();
    $("#admin-card").classList.toggle("hidden", !privileged);
    $("#approvals-card").classList.toggle("hidden", !privileged);

    await Promise.all([loadDirectory(), loadWallets(), loadTransactions()]);
    if (privileged) {
      await Promise.all([loadTrialBalance(), loadApprovals()]);
    }
  }

  async function loadDirectory() {
    try {
      state.directory = await api("GET", "/api/v1/demo/directory");
    } catch (e) {
      state.directory = [];
    }
    $$(".directory-select").forEach((select) => {
      select.innerHTML = "";
      state.directory
        .filter((d) => String(d.userId) !== state.user.id)
        .forEach((d) => {
          const opt = document.createElement("option");
          opt.value = d.userId;
          opt.textContent = `${d.email} (${d.currency})`;
          select.appendChild(opt);
        });
    });
  }

  async function loadWallets() {
    try {
      const wallets = await api("GET", "/api/v1/wallets/me/list");
      const list = $("#wallets-list");
      list.innerHTML = "";
      if (!wallets.length) {
        list.innerHTML = '<p class="muted">No wallets yet.</p>';
        return;
      }
      wallets.forEach((w) => {
        const row = document.createElement("div");
        row.className = "wallet-row";
        const flags = [
          w.frozen ? "frozen" : null,
          w.fraudLocked ? "fraud-locked" : null,
        ]
          .filter(Boolean)
          .join(" · ");
        row.innerHTML = `
          <div>
            <div>${escapeHtml(w.label || "Wallet")}</div>
            <div class="ccy">${escapeHtml(w.currency)}${flags ? " · " + flags : ""}</div>
          </div>
          <div class="amount">${fmtMoney(w.balanceMinor, w.currency)}</div>`;
        list.appendChild(row);
      });
    } catch (e) {
      toast("Could not load wallets: " + e.message, true);
    }
  }

  function statusPillClass(status) {
    if (status === "POSTED") return "status-posted";
    if (status === "PENDING" || status === "PENDING_APPROVAL") return "status-pending";
    return "status-failed";
  }

  async function loadTransactions() {
    try {
      const page = await api("GET", "/api/v1/transactions/me?page=0&size=20");
      const tbody = $("#txn-tbody");
      const empty = $("#txn-empty");
      tbody.innerHTML = "";
      const rows = page.content || [];
      empty.classList.toggle("hidden", rows.length > 0);
      rows.forEach((txn) => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
          <td><button type="button" class="btn btn-ghost btn-small expand-btn" aria-label="Show journal">+</button></td>
          <td>${escapeHtml(txn.type)}</td>
          <td><span class="status-pill ${statusPillClass(txn.status)}">${escapeHtml(txn.status)}</span></td>
          <td>${escapeHtml(txn.currency)}</td>
          <td>${escapeHtml(fmtWhen(txn.createdAt))}</td>
          <td><code>${escapeHtml(txn.publicId)}</code></td>
          <td>${
            txn.status === "POSTED"
              ? '<button type="button" class="btn btn-ghost btn-small reverse-btn">Reverse</button>'
              : ""
          }</td>`;

        const journalRow = document.createElement("tr");
        journalRow.innerHTML =
          '<td colspan="7"><div class="journal-lines hidden"></div></td>';
        const journalHolder = $(".journal-lines", journalRow);

        $(".expand-btn", tr).addEventListener("click", async (ev) => {
          const btn = ev.currentTarget;
          const showing = !journalHolder.classList.contains("hidden");
          if (showing) {
            journalHolder.classList.add("hidden");
            btn.textContent = "+";
            return;
          }
          journalHolder.classList.remove("hidden");
          btn.textContent = "−";
          if (!journalHolder.dataset.loaded) {
            journalHolder.innerHTML = '<p class="muted">Loading journal…</p>';
            try {
              const lines = await api(
                "GET",
                `/api/v1/transactions/${txn.publicId}/journal-entries`
              );
              journalHolder.innerHTML = lines.length
                ? lines
                    .map(
                      (l) =>
                        `<div class="journal-line"><span class="${l.direction.toLowerCase()}">${escapeHtml(
                          l.direction
                        )} · ${escapeHtml(l.accountCode)}</span><span>${fmtMoney(
                          l.amountMinor,
                          l.currency
                        )}</span></div>`
                    )
                    .join("")
                : '<p class="muted">No journal lines.</p>';
              journalHolder.dataset.loaded = "1";
            } catch (e) {
              journalHolder.innerHTML =
                '<p class="muted">Failed to load journal.</p>';
            }
          }
        });

        const reverseBtn = $(".reverse-btn", tr);
        if (reverseBtn) {
          reverseBtn.addEventListener("click", async () => {
            try {
              await api("POST", "/api/v1/payments/reverse", {
                originalPublicId: txn.publicId,
                idempotencyKey: crypto.randomUUID(),
              });
              toast("Transaction reversed");
              await refreshMoneyViews();
            } catch (e) {
              toast("Reversal failed: " + e.message, true);
            }
          });
        }

        tbody.appendChild(tr);
        tbody.appendChild(journalRow);
      });
    } catch (e) {
      toast("Could not load transactions: " + e.message, true);
    }
  }

  async function loadTrialBalance() {
    const currency = $("#trial-currency")?.value || "USD";
    try {
      const tb = await api(
        "GET",
        `/api/v1/reports/trial-balance?currency=${encodeURIComponent(currency)}`
      );
      const el = $("#trial-balance");
      el.innerHTML = `
        <div class="balance-banner ${tb.balanced ? "" : "warn"}">
          <strong>${tb.balanced ? "Balanced" : "Out of balance"}</strong>
          <span>debits ${fmtMoney(tb.totalDebitsMinor, tb.currency)}</span>
          <span>credits ${fmtMoney(tb.totalCreditsMinor, tb.currency)}</span>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead><tr><th>Account</th><th>Type</th><th>Debit</th><th>Credit</th><th>Balance</th></tr></thead>
            <tbody>
              ${(tb.lines || [])
                .map(
                  (l) =>
                    `<tr><td>${escapeHtml(l.code)}</td><td>${escapeHtml(
                      l.type
                    )}</td><td>${fmtMoney(l.debitMinor, tb.currency)}</td><td>${fmtMoney(
                      l.creditMinor,
                      tb.currency
                    )}</td><td>${fmtMoney(l.balanceMinor, tb.currency)}</td></tr>`
                )
                .join("")}
            </tbody>
          </table>
        </div>`;
    } catch (e) {
      $("#trial-balance").innerHTML =
        '<p class="muted">Trial balance unavailable for this role or currency.</p>';
    }
  }

  async function loadApprovals() {
    const list = $("#approvals-list");
    try {
      const pending = await api("GET", "/api/v1/approvals/pending");
      list.innerHTML = "";
      if (!pending.length) {
        list.innerHTML =
          '<p class="muted">No pending approvals right now.</p>';
        return;
      }
      pending.forEach((a) => {
        const row = document.createElement("div");
        row.className = "approval-row";
        const amount =
          a.payload && a.payload.amountMinor != null
            ? fmtMoney(a.payload.amountMinor, a.payload.currency || "USD")
            : "—";
        row.innerHTML = `
          <div>
            <div><strong>${escapeHtml(a.operationType || "WITHDRAWAL")}</strong> · ${amount}</div>
            <div class="meta">${escapeHtml(a.id)} · ${escapeHtml(fmtWhen(a.createdAt))}</div>
          </div>
          <div class="approval-actions">
            <button type="button" class="btn btn-primary btn-small approve-btn">Approve</button>
            <button type="button" class="btn btn-danger btn-small reject-btn">Reject</button>
          </div>`;
        $(".approve-btn", row).addEventListener("click", async () => {
          try {
            await api("POST", `/api/v1/approvals/${a.id}/approve`);
            toast("Withdrawal approved");
            await Promise.all([loadApprovals(), refreshMoneyViews()]);
          } catch (e) {
            toast("Approve failed: " + e.message, true);
          }
        });
        $(".reject-btn", row).addEventListener("click", async () => {
          try {
            await api("POST", `/api/v1/approvals/${a.id}/reject`, {
              reason: "Rejected from demo UI",
            });
            toast("Withdrawal rejected");
            await loadApprovals();
          } catch (e) {
            toast("Reject failed: " + e.message, true);
          }
        });
        list.appendChild(row);
      });
    } catch (e) {
      list.innerHTML = '<p class="muted">Could not load approvals.</p>';
    }
  }

  async function refreshMoneyViews() {
    const tasks = [loadWallets(), loadTransactions()];
    if (isPrivileged()) {
      tasks.push(loadTrialBalance());
      tasks.push(loadApprovals());
    }
    await Promise.all(tasks);
  }

  function toMinor(amountStr) {
    return Math.round(parseFloat(amountStr) * 100);
  }

  function wireTabs() {
    $$(".tab").forEach((tab) => {
      tab.addEventListener("click", () => {
        $$(".tab").forEach((t) => {
          t.classList.remove("active");
          t.setAttribute("aria-selected", "false");
        });
        tab.classList.add("active");
        tab.setAttribute("aria-selected", "true");
        $$(".tab-panel").forEach((p) => p.classList.add("hidden"));
        $(`#${tab.dataset.tab}-form`).classList.remove("hidden");
      });
    });
  }

  function wireForms() {
    $("#login-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      doLogin(f.get("email"), f.get("password"));
    });

    $("#register-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      doRegister(f.get("email"), f.get("password"), f.get("preferredCurrency"));
    });

    $("#logout-btn").addEventListener("click", logout);
    $("#clear-log-btn").addEventListener(
      "click",
      () => ($("#call-log").innerHTML = "")
    );
    $("#refresh-approvals-btn")?.addEventListener("click", () => loadApprovals());
    $("#trial-currency")?.addEventListener("change", () => loadTrialBalance());

    $("#deposit-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      try {
        await api("POST", "/api/v1/demo/self-deposit", {
          amountMinor: toMinor(f.get("amount")),
          currency: f.get("currency"),
        });
        toast("Funds settled via deposit posting");
        await refreshMoneyViews();
      } catch (e2) {
        toast("Deposit failed: " + e2.message, true);
      }
    });

    $("#transfer-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      try {
        await api("POST", "/api/v1/payments/transfer", {
          toUserId: f.get("toUserId"),
          amountMinor: toMinor(f.get("amount")),
          currency: f.get("currency"),
          idempotencyKey: crypto.randomUUID(),
        });
        toast("Transfer posted");
        await refreshMoneyViews();
      } catch (e2) {
        toast("Transfer failed: " + e2.message, true);
      }
    });

    $("#merchant-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      try {
        await api("POST", "/api/v1/payments/merchant", {
          merchantUserId: f.get("merchantUserId"),
          grossMinor: toMinor(f.get("gross")),
          feeMinor: toMinor(f.get("fee")),
          currency: f.get("currency"),
          idempotencyKey: crypto.randomUUID(),
        });
        toast("Merchant payment posted");
        await refreshMoneyViews();
      } catch (e2) {
        toast("Payment failed: " + e2.message, true);
      }
    });

    $("#withdraw-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      try {
        const res = await api("POST", "/api/v1/payments/withdraw", {
          amountMinor: toMinor(f.get("amount")),
          currency: f.get("currency"),
          idempotencyKey: crypto.randomUUID(),
        });
        if (res && res.status === "PENDING_APPROVAL") {
          toast("Withdrawal pending maker/checker approval");
        } else {
          toast("Withdrawal posted");
        }
        await refreshMoneyViews();
      } catch (e2) {
        toast("Withdrawal failed: " + e2.message, true);
      }
    });
  }

  function init() {
    renderQuickLogins();
    wireTabs();
    wireForms();
    if (state.token) {
      const claims = decodeJwt(state.token);
      if (claims && claims.exp * 1000 > Date.now()) {
        state.user = {
          id: String(claims.sub),
          email: claims.email,
          roles: claims.roles || [],
        };
        enterApp().catch(() => logout());
        return;
      }
    }
    logout();
  }

  document.addEventListener("DOMContentLoaded", init);
})();
