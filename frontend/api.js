/* API-клиент StaffMatch. Все запросы несут X-Max-Init-Data (window.WebApp.initData). */

const INIT_DATA_KEY = 'fp_initdata';

// Авторизация извне: внутри MAX-веб-приложения window.WebApp.initData, иначе — сохранённое в localStorage.
function getInitData() {
  const web = window.WebApp;
  if (web && typeof web.initData === 'string' && web.initData) return web.initData;
  return localStorage.getItem(INIT_DATA_KEY) || '';
}

function setInitData(value) {
  localStorage.setItem(INIT_DATA_KEY, value);
}

class ApiError extends Error {
  constructor(status, detail) {
    super(detail || ('HTTP ' + status));
    this.status = status;
  }
}

async function api(path, options = {}) {
  if (typeof isDemoMode === 'function' && isDemoMode()) {
    const demoRes = await demoRequest(path, options);
    if (demoRes && demoRes.ok === false) throw new ApiError(demoRes.status, demoRes.detail);
    return demoRes;
  }
  const initData = getInitData();
  if (!initData) throw new ApiError(401, 'MAX initData не указан');

  const headers = { 'X-Max-Init-Data': initData };
  if (options.body) headers['Content-Type'] = 'application/json';

  const res = await fetch(FP_CONFIG.apiBase + path, {
    method: options.method || 'GET',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined
  });

  if (res.status === 204) return null;
  let data = null;
  try { data = await res.json(); } catch (_) { /* пустое тело */ }

  if (!res.ok) {
    let detail = res.statusText;
    if (data) {
      if (data.detail) detail = typeof data.detail === 'string' ? data.detail : detail;
      else if (data.message) detail = data.message;
    }
    throw new ApiError(res.status, detail);
  }
  return data;
}

// Генератор подписанной initData для локального теста в браузере.
// Логика дублирует MaxInitDataFixtures / MaxInitDataValidator Java-бэкенда.
async function generateInitData(botToken, userId) {
  const enc = new TextEncoder();
  const hmacKey = await crypto.subtle.importKey('raw', enc.encode('WebAppData'),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const secret = await crypto.subtle.sign('HMAC', hmacKey, enc.encode(botToken));
  const secretKey = await crypto.subtle.importKey('raw', secret,
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);

  const authDate = Math.floor(Date.now() / 1000);
  const userJson = JSON.stringify({ id: Number(userId), first_name: 'Dev User' });
  const checkString = 'auth_date=' + authDate + '\nuser=' + userJson;
  const hashBuf = await crypto.subtle.sign('HMAC', secretKey, enc.encode(checkString));
  const hash = [...new Uint8Array(hashBuf)].map(b => b.toString(16).padStart(2, '0')).join('');

  // Кодирование как в Java: URLEncoder(...).replace("+","%20").
  // encodeURIComponent уже даёт %20 для пробелов и не использует "+".
  // Java дополнительно кодирует ~ ! ' ( ) — воспроизводим это вручную.
  const encodedUser = encodeURIComponent(userJson)
    .replace(/~/g, '%7E').replace(/!/g, '%21')
    .replace(/'/g, '%27').replace(/\(/g, '%28').replace(/\)/g, '%29');
  return 'user=' + encodedUser + '&auth_date=' + authDate + '&hash=' + hash;
}