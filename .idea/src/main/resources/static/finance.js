/* ═══════════════════════════════════════════════
   CeremonyConnect — finance.js  (Finance Office)
   ═══════════════════════════════════════════════ */

const API = 'http://localhost:8080/api';
let finSession = null;

/* ── Helpers ──────────────────────────────────── */
function basicAuth(email, password) { return 'Basic ' + btoa(email + ':' + password); }
function getAuthHeader() { return finSession ? basicAuth(finSession.email, finSession.rawPassword) : ''; }

function toast(msg, type = 'info', duration = 3500) {
  const t = document.createElement('div');
  t.className = `toast ${type}`;
  t.textContent = msg;
  document.getElementById('toast-container').appendChild(t);
  t.onclick = () => t.remove();
  setTimeout(() => t.remove(), duration);
}

async function apiCall(method, path, body = null) {
  const headers = { Authorization: getAuthHeader() };
  if (body) headers['Content-Type'] = 'application/json';
  const res = await fetch(API + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : null
  });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.message || data.error || 'Request failed');
  return data;
}

/* ── Auth ─────────────────────────────────────── */
function openLogin() { /* login card is visible by default */ }

async function doFinanceLogin(e) {
  e.preventDefault();
  const email    = document.getElementById('fin-email').value.trim();
  const password = document.getElementById('fin-password').value;

  try {
    const data = await fetch(API + '/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password })
    }).then(r => { if (!r.ok) throw new Error('Invalid credentials'); return r.json(); });

    if (!['FINANCE_OFFICER', 'ADMIN'].includes(data.role)) {
      throw new Error('Access denied: Finance Officer role required.');
    }

    finSession = { ...data, rawPassword: password };
    document.getElementById('welcome-msg').textContent = `👤 ${data.fullName}`;
    document.getElementById('auth-btn').classList.add('hidden');
    document.getElementById('logout-btn').classList.remove('hidden');
    document.getElementById('login-card').classList.add('hidden');
    document.getElementById('dashboard').classList.remove('hidden');

    toast(`Welcome, ${data.fullName}! 💰`, 'success');
    loadDashboard();
  } catch (err) {
    toast(err.message, 'error');
  }
}

function logout() {
  finSession = null;
  location.reload();
}

/* ── Tab switching ────────────────────────────── */
function switchTab(name) {
  document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
  document.getElementById('tab-' + name).classList.add('active');
  event.currentTarget.classList.add('active');

  if (name === 'report') loadReport();
  if (name === 'logs')   loadAuditLogs();
}

/* ── Dashboard ────────────────────────────────── */
async function loadDashboard() {
  loadPendingSlips();
}

/* ── Pending Payment Slips ────────────────────── */
async function loadPendingSlips() {
  try {
    const slips = await apiCall('GET', '/finance/pending-slips');
    document.getElementById('stat-pending').textContent = slips.length;
    renderSlips(slips);
  } catch (err) {
    toast('Failed to load pending slips: ' + err.message, 'error');
  }
}

function renderSlips(slips) {
  const el = document.getElementById('slips-list');
  if (!slips.length) {
    el.innerHTML = `
      <div class="card text-center" style="padding:3rem">
        <p style="font-size:2rem;margin-bottom:.5rem">🎉</p>
        <p style="color:var(--text-muted)">All payment slips have been reviewed!</p>
      </div>`;
    return;
  }

  el.innerHTML = `
    <div class="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Slip #</th>
            <th>Reservation #</th>
            <th>Customer</th>
            <th>Amount (RM)</th>
            <th>File</th>
            <th>Uploaded At</th>
            <th>Status</th>
            <th>Action</th>
          </tr>
        </thead>
        <tbody>
          ${slips.map(s => `
            <tr>
              <td><strong>#${s.id}</strong></td>
              <td>#${s.reservation?.id || '—'}</td>
              <td>
                <div>${s.reservation?.customer?.fullName || '—'}</div>
                <small style="color:var(--text-muted)">${s.reservation?.customer?.email || ''}</small>
              </td>
              <td><strong>RM ${Number(s.reservation?.totalCost || 0).toLocaleString('en-LK',{minimumFractionDigits:2})}</strong></td>
              <td>
                <a class="btn btn-outline btn-sm" href="${API}/finance/slip-file/${s.id}" target="_blank">
                  📄 View
                </a>
              </td>
              <td>${new Date(s.uploadedAt).toLocaleString()}</td>
              <td><span class="badge badge-pending">${s.verificationStatus}</span></td>
              <td>
                <button class="btn btn-primary btn-sm"
                  onclick="openVerifyModal(${s.id}, ${s.reservation?.id}, ${s.reservation?.totalCost}, '${s.originalFileName}')">
                  🔍 Verify
                </button>
              </td>
            </tr>
          `).join('')}
        </tbody>
      </table>
    </div>
  `;
}

/* ── Revenue Report ───────────────────────────── */
async function loadReport() {
  const el = document.getElementById('report-content');
  el.innerHTML = '<p class="text-center" style="color:var(--text-muted)">Loading…</p>';

  try {
    const report = await apiCall('GET', '/admin/report');
    const counts = report.bookingCountByStatus || {};

    document.getElementById('stat-revenue').textContent =
      'LKR ' + Number(report.totalRevenue || 0).toLocaleString('en-LK', { minimumFractionDigits: 2 });
    document.getElementById('stat-bookings').textContent = report.totalBookings || 0;

    const statusRows = Object.entries(counts)
      .map(([s, c]) => `<tr><td>${s}</td><td><strong>${c}</strong></td></tr>`).join('');

    el.innerHTML = `
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:2rem;flex-wrap:wrap">
        <div>
          <h3 style="margin-bottom:1rem;color:var(--primary)">📊 Booking Volume by Status</h3>
          <div class="table-wrap">
            <table>
              <thead><tr><th>Status</th><th>Count</th></tr></thead>
              <tbody>${statusRows}</tbody>
            </table>
          </div>
        </div>
        <div>
          <h3 style="margin-bottom:1rem;color:var(--primary)">💵 Revenue Summary</h3>
          <div class="stat-card" style="margin-bottom:1rem">
            <div class="stat-value">RM ${Number(report.totalRevenue || 0).toLocaleString('en-LK',{minimumFractionDigits:2})}</div>
            <div class="stat-label">Total Confirmed Revenue</div>
          </div>
          <div class="stat-card">
            <div class="stat-value">${report.totalBookings}</div>
            <div class="stat-label">Total Reservations</div>
          </div>
        </div>
      </div>
    `;
  } catch (err) {
    el.innerHTML = `<p style="color:var(--danger)">Failed to load report: ${err.message}</p>`;
  }
}

/* ── Audit Logs ───────────────────────────────── */
async function loadAuditLogs() {
  const el = document.getElementById('logs-list');
  el.innerHTML = '<p class="text-center" style="color:var(--text-muted)">Loading logs…</p>';

  try {
    const logs = await apiCall('GET', '/admin/audit-logs');
    if (!logs.length) {
      el.innerHTML = '<p class="text-center" style="color:var(--text-muted)">No audit logs yet.</p>';
      return;
    }
    el.innerHTML = `
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Timestamp</th>
              <th>Action</th>
              <th>Entity</th>
              <th>Entity #</th>
              <th>Performed By</th>
              <th>Details</th>
            </tr>
          </thead>
          <tbody>
            ${logs.map(l => `
              <tr>
                <td style="white-space:nowrap;font-size:.8rem">${new Date(l.timestamp).toLocaleString()}</td>
                <td><strong>${l.action}</strong></td>
                <td>${l.entityType}</td>
                <td>${l.entityId || '—'}</td>
                <td style="font-size:.85rem">${l.performedBy}</td>
                <td style="font-size:.82rem;max-width:260px;overflow:hidden;text-overflow:ellipsis">${l.details || '—'}</td>
              </tr>
            `).join('')}
          </tbody>
        </table>
      </div>
    `;
  } catch (err) {
    el.innerHTML = `<p style="color:var(--danger)">Failed to load audit logs: ${err.message}</p>`;
  }
}

/* ── Verify Modal ─────────────────────────────── */
let currentSlipId = null;

function openVerifyModal(slipId, resId, amount, fileName) {
  currentSlipId = slipId;
  document.getElementById('verify-slip-id').textContent = slipId;
  document.getElementById('verify-res-id').textContent  = resId;
  document.getElementById('verify-amount').textContent  =
    'LKR ' + Number(amount || 0).toLocaleString('en-LK', { minimumFractionDigits: 2 });
  document.getElementById('verify-file-link').textContent = fileName || 'View File';
  document.getElementById('verify-file-link').href = `${API}/finance/slip-file/${slipId}`;
  document.getElementById('verify-decision').value = '';
  document.getElementById('verify-remarks').value  = '';
  document.getElementById('verify-modal').classList.remove('hidden');
}

function closeVerifyModal() {
  document.getElementById('verify-modal').classList.add('hidden');
  currentSlipId = null;
}

async function submitVerification(e) {
  e.preventDefault();
  const approved = document.getElementById('verify-decision').value === 'true';
  const remarks  = document.getElementById('verify-remarks').value.trim();

  try {
    const result = await apiCall('POST', `/finance/verify/${currentSlipId}`, { approved, remarks });
    if (result.invoiceNumber) {
      toast(`✅ Invoice ${result.invoiceNumber} generated! Total: RM ${Number(result.totalAmount).toLocaleString()}`, 'success', 6000);
    } else {
      toast('Payment slip rejected.', 'warning');
    }
    closeVerifyModal();
    loadPendingSlips();
    loadReport();
  } catch (err) {
    toast('❌ ' + err.message, 'error', 6000);
  }
}

/* ── Init ─────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('dashboard').classList.add('hidden');
});
