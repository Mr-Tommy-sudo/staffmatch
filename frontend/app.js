/* StaffMatch — мобильный веб-апп (вертикальные экраны).
   Интеграция с Java-бэкендом: /api/v1/me, role, candidate/employer. */

const WORK_FORMATS = { REMOTE: 'Удалённо', HYBRID: 'Гибрид', OFFICE: 'Офис' };
const TEST_MODES = { NONE: 'Без теста', AUTO: 'Автотест', CUSTOM: 'Свой тест' };
const LEVELS = ['JUNIOR', 'MIDDLE', 'SENIOR'];
const SUBCATS = [
  ['Найм сотрудников', 'Компания ищем таланты', 'company'],
  ['Поиск работы', 'Соискатель ищет работу', 'candidate']
];

const SKILL_CODES = ['B2B_SALES', 'NEGOTIATION', 'KEY_ACCOUNTS', 'CRM', 'COLD_CALLS', 'RETAIL', 'CUSTOMER_SERVICE',
  'ACCOUNTING', '1C', 'EXCEL', 'REPORTING', 'AUDIT', 'TAX', 'HANDBOOK',
  'DRIVING_B', 'DRIVING_C', 'TRUCK', 'LOGISTICS', 'WAREHOUSE', 'FORKLIFT', 'FORWARDING',
  'CONSTRUCTION', 'MACHINERY', 'TOOLS', 'ELECTRICAL', 'PLUMBING', 'HEIGHT_WORK',
  'MEDICINE', 'PHARMACY', 'FIRST_AID', 'NUTRITION', 'COSMETOLOGY',
  'COOKING', 'BAKING', 'HOSPITALITY', 'CLEANING', 'LAUNDRY', 'KITCHEN',
  'GARDENING', 'LANSCAPE', 'SECURITY', 'FIRE_SAFETY', 'EDUCATION', 'TEACHING',
  'SEO', 'SMM', 'MARKETING', 'ANALYTICS', 'PROJECT_MGMT', 'LEADERSHIP', 'MENTORING', "JAVA_CORE", 'SPRING', 'POSTGRESQL'];

const state = {
  view: 'boot',
  me: null,                 // { id, role }
  candidate: null,          // профиль кандидата
  assignments: [],          // назначенные тесты
  invitations: [],          // приглашения
  candidateListErrors: { assignments: null, invitations: null },
  testSession: null,        // { assignment, questions, answers, deadline }
  vacancies: [],            // вакансии нанимателя
  vacancy: null,            // текущая вакансия
  matches: [],              // матчи вакансии
  ranking: null,            // { final, waiting }
  results: [],              // результаты по вакансии
  vacancyTests: null,       // тест вакансии (ответы нанимателя)
  tab: 'profile',           // активная вкладка нижней навигации
  vacancyTab: 'overview',   // вкладка внутри вакансии
  editProfile: false,
  editDraft: null,
  pendingRole: null,
};

const $ = (id) => document.getElementById(id);

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function money(v) {
  if (v == null) return null;
  return Number(v).toLocaleString('ru-RU') + ' ₽';
}

function shortId(id) {
  return String(id).slice(0, 8);
}

function toast(msg, ms = 2400) {
  let wrap = document.querySelector('.toast-wrap');
  if (!wrap) { wrap = document.createElement('div'); wrap.className = 'toast-wrap'; document.body.appendChild(wrap); }
  const t = document.createElement('div');
  t.className = 'toast';
  t.textContent = msg;
  wrap.appendChild(t);
  setTimeout(() => { t.style.opacity = '0'; t.style.transition = 'opacity .3s'; }, ms);
  setTimeout(() => t.remove(), ms + 350);
}

function plural(n, one, few, many) {
  const m10 = n % 10, m100 = n % 100;
  if (m10 === 1 && m100 !== 11) return one;
  if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return few;
  return many;
}

/* ===================== ЗАГРУЗКА ===================== */
async function boot() {
  const hasInitData = !!getInitData();
  if (!hasInitData && FP_CONFIG.requireInitDataScreen) {
    showView('connect');
    return;
  }
  showView('boot');
  try {
    state.me = await api('/api/v1/me');
    if (!state.me.role) { showRoleSelect(); return; }
    await routeByRole();
  } catch (e) {
    if (e.status === 401) { showView('connect'); return; }
    bootError(e);
  }
}

function bootError(e) {
  document.getElementById('boot-error').textContent = (e && e.message) ? e.message : 'Не удалось подключиться к серверу';
  document.getElementById('boot-error').classList.remove('hidden');
  showView('connect');
}

function showRoleSelect() {
  state.view = 'role';
  render('role-screen', roleScreen());
  showView('role');
}

async function chooseRole(role) {
  try {
    state.pendingRole = role;
    state.me = await api('/api/v1/me/role', { method: 'PUT', body: { role } });
    await routeByRole();
  } catch (e) {
    toast('Ошибка: ' + e.message);
  } finally { state.pendingRole = null; }
}

function routeByRole() {
  if (state.me.role === 'EMPLOYER') return initEmployer();
  if (state.me.role === 'CANDIDATE') return initCandidate();
  showRoleSelect();
}

/* ===================== ЛОГИКА КАНДИДАТА ===================== */
async function initCandidate() {
  state.view = 'candidate';
  state.tab = 'profile';
  renderHero('candidate');
  await refreshCandidateProfile();
  await refreshCandidateLists();
  renderShell('candidate');
  showView('candidate');
  renderTab();
}

async function refreshCandidateProfile() {
  try {
    state.candidate = await api('/api/v1/candidate/profile');
    state.editProfile = !state.candidate;
  } catch (e) {
    if (e.status === 404) state.candidate = null;
  }
}

async function refreshCandidateLists() {
  const results = await Promise.allSettled([
    api('/api/v1/candidate/test-assignments'),
    api('/api/v1/candidate/invitations')
  ]);
  state.assignments = results[0].status === 'fulfilled' ? results[0].value : [];
  state.invitations = results[1].status === 'fulfilled' ? results[1].value : [];
  state.candidateListErrors = {
    assignments: results[0].status === 'rejected' ? results[0].reason : null,
    invitations: results[1].status === 'rejected' ? results[1].reason : null
  };
  results.forEach((result, i) => {
    if (result.status === 'rejected' && result.reason.status !== 401) {
      toast(`Не удалось загрузить ${i ? 'приглашения' : 'тесты'}: ${result.reason.message}`);
    }
  });
  const unauthorized = results.find(result => result.status === 'rejected' && result.reason.status === 401);
  if (unauthorized) {
    showView('connect');
    throw unauthorized.reason;
  }
}

/* ---------- Профиль кандидата ---------- */
function profileForm() {
  if (!state.editProfile) return profileView(state.candidate || {});
  return profileEditForm(state.editDraft || state.candidate || {});
}

function profileView(c) {
  const levelFact = levelWord(c.experienceMonths);
  const wf = c.workFormats || [];
  const initial = esc(String(state.me?.id || 'К').slice(0, 1).toUpperCase());
  return `
  <div class="content screen-enter">
    <div class="p-card">
      <div class="p-avatar">${initial}</div>
      <div class="p-head">
        <div class="p-city">${esc(c.city || 'Кандидат')}</div>
        <div class="p-meta">ID ${esc(state.me?.id || '—')} · ${levelFact}</div>
      </div>
      <span class="p-chip">Кандидат</span>
    </div>

    <div class="p-chips">
      ${wf.length ? wf.map(w => `<span class="chip chip-violet">${esc(WORK_FORMATS[w] || w)}</span>`).join('') : '<span class="chip chip-gray">Формат не указан</span>'}
    </div>

    <div class="stats-grid">
      <div class="stat s-money">
        <div class="s-num">${money(c.salaryExpectation) || '—'}</div>
        <div class="s-lbl">Зарплата</div>
      </div>
      <div class="stat s-exp">
        <div class="s-num">${c.experienceMonths ? c.experienceMonths + ' мес' : '—'}</div>
        <div class="s-lbl">Опыт</div>
      </div>
      <div class="stat s-hours">
        <div class="s-num">${c.availableHours ? c.availableHours + ' ч' : '—'}</div>
        <div class="s-lbl">Нагрузка</div>
      </div>
    </div>

    <div class="card">
      <div class="c-t">
        <h3>Навыки</h3><span class="chip chip-violet">${(c.skills || []).length}</span>
      </div>
      <div class="skill-cloud">
        ${(c.skills || []).map(s => levelDots(s)).join('') || '<p class="soft" style="font-size:13px">Навыки не указаны</p>'}
      </div>
    </div>

    ${c.portfolioUrl ? `
    <div class="card p-mini">
      <div class="p-ico">🔗</div>
      <div>
        <div class="row-title">Портфолио</div>
        <a class="p-link" href="${esc(c.portfolioUrl)}" target="_blank" rel="noopener">${esc(c.portfolioUrl)}</a>
      </div>
    </div>` : ''}

    <button class="btn btn-ghost btn-block" style="margin:2px 0 20px" onclick="toggleEditProfile()">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px"><path d="M17 3a2.8 2.8 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg>
      Редактировать профиль
    </button>
  </div>`;
}

function levelDots(s) {
  const n = Math.max(1, Math.min(5, s.level || 1));
  return `<div class="lv"><span>${esc(s.code)}</span><span class="dots">${'●'.repeat(n)}<i>${'○'.repeat(5 - n)}</i></span></div>`;
}

function levelWord(months) {
  if (!months) return 'Опыт не указан';
  if (months < 12) return 'Стажёр';
  if (months < 36) return 'Junior/Middle';
  return 'Senior';
}

function profileEditForm(c) {
  return `
  <div class="content screen-enter">
    <div class="card">
      <h3>Основные данные</h3>
      <div class="field"><label>Город</label><input class="input" id="edit-city" value="${esc(c.city || '')}" placeholder="Москва"></div>
      <div class="field">
        <div class="label-hint"><label>Формат работы</label><span>обязательно</span></div>
        <div class="opt-row">
          ${Object.entries(WORK_FORMATS).map(([k, v], i) => `
            <button type="button" class="opt ${(c.workFormats || []).includes(k) ? 'active' : ''}" data-wf="${k}" onclick="toggleWF('${k}')">${v}</button>`).join('')}
        </div>
      </div>
      <div class="field"><label>Часы в неделю</label><input class="input" id="edit-hours" type="number" min="0" max="168" value="${esc(c.availableHours ?? '')}" placeholder="до 40"></div>
      <div class="field"><label>Зарплатные ожидания (₽/мес)</label><input class="input" id="edit-salary" type="number" min="0" value="${esc(c.salaryExpectation ?? '')}" placeholder="100000"></div>
      <div class="field"><label>Опыт (месяцев)</label><input class="input" id="edit-experience" type="number" min="0" value="${esc(c.experienceMonths ?? '')}" placeholder="24"></div>
      <div class="field"><label>Портфолио</label><input class="input" id="edit-portfolio" value="${esc(c.portfolioUrl || '')}" placeholder="https://..."></div>
    </div>
    <div class="card">
      <h3>Навыки <span class="soft" style="font-size:12px;font-weight:600">(код + уровень 1–5)</span></h3>
      <div id="cand-skills">
        ${candSkillChips()}
      </div>
      <div class="skill-add">
        <input class="input" id="new-skill-code" list="skill-codes" placeholder="Код навыка">
        <datalist id="skill-codes">${SKILL_CODES.map(s => `<option value="${s}">`).join('')}</datalist>
        <select class="input" id="new-skill-level"><option value="1">1</option><option value="2">2</option><option value="3" selected>3</option><option value="4">4</option><option value="5">5</option></select>
        <button class="btn btn-primary btn-sm" onclick="addCandSkill()">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="width:16px;height:16px"><path d="M12 5v14M5 12h14"/></svg>
        </button>
      </div>
    </div>
    <div class="edit-actions">
      <button class="btn-icon btn-icon-cancel" onclick="cancelEditProfile()" aria-label="Отмена">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
      </button>
      <button class="btn btn-primary" onclick="saveCandidateProfile()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="width:18px;height:18px"><path d="M12 20V9"/><path d="m6 14 6-6 6 6"/></svg>
        Сохранить профиль
      </button>
    </div>
  </div>
  `;
}

function candSkillChips() {
  const d = state.editDraft || state.candidate || {};
  return (d.skills || []).map(s =>
    `<span class="skill-chip">${esc(s.code)} L${s.level} <button onclick="removeCandSkill('${esc(s.code)}')">✕</button></span>`
  ).join('') || '<p class="soft" style="font-size:13px">Добавьте хотя бы один навык</p>';
}

function addCandSkill() {
  const code = $('new-skill-code').value.trim().toUpperCase();
  const level = Number($('new-skill-level').value);
  if (!code) return toast('Введите код навыка');
  const draft = state.editDraft;
  if (!draft) return;
  const skills = draft.skills || [];
  if (skills.some(s => s.code === code)) return toast('Навык уже добавлен');
  draft.skills = draft.skills || [];
  draft.skills.push({ code, level });
  rerenderCardField('cand-skills', candSkillChips());
  $('new-skill-code').value = '';
}

function removeCandSkill(code) {
  const draft = state.editDraft;
  if (!draft) return;
  draft.skills = (draft.skills || []).filter(s => s.code !== code);
  rerenderCardField('cand-skills', candSkillChips());
}

function toggleWF(format) {
  const draft = state.editDraft;
  if (!draft) return;
  draft.workFormats = draft.workFormats || [];
  const list = draft.workFormats;
  const i = list.indexOf(format);
  if (i >= 0) list.splice(i, 1); else list.push(format);
  document.querySelectorAll('[data-wf]').forEach(e => e.classList.toggle('active', list.includes(e.dataset.wf)));
}

function rerenderCardField(id, html) {
  const el = $(id);
  if (el) el.innerHTML = html;
  else renderTab();
}

function scrollViewTop(viewName) {
  const view = document.getElementById('view-' + (viewName || state.view));
  requestAnimationFrame(() => {
    if (view) view.scrollTop = 0;
    window.scrollTo(0, 0);
    document.body.scrollTop = 0;
    document.documentElement.scrollTop = 0;
  });
  setTimeout(() => {
    if (view) view.scrollTop = 0;
    window.scrollTo(0, 0);
    document.body.scrollTop = 0;
    document.documentElement.scrollTop = 0;
  }, 40);
}

function toggleEditProfile() {
  state.editProfile = !state.editProfile;
  if (state.editProfile) {
    state.editDraft = JSON.parse(JSON.stringify(state.candidate || { skills: [], workFormats: [] }));
  }
  renderTab();
  scrollViewTop();
}

function cancelEditProfile() {
  state.editProfile = false;
  state.editDraft = null;
  renderTab();
  scrollViewTop();
}

async function saveCandidateProfile() {
  const draft = state.editDraft;
  if (!draft) return;
  const city = $('edit-city').value.trim();
  const workFormats = draft.workFormats || [];
  const skills = draft.skills || [];
  const body = {
    city: city || null,
    workFormats,
    availableHours: numberOrNull('edit-hours'),
    salaryExpectation: numberOrNull('edit-salary'),
    experienceMonths: numberOrNull('edit-experience'),
    portfolioUrl: $('edit-portfolio').value.trim() || null,
    skills
  };
  if (!workFormats.length) return toast('Выберите формат работы');
  if (!skills.length) return toast('Добавьте хотя бы один навык');
  try {
    state.candidate = await api('/api/v1/candidate/profile', { method: 'PUT', body });
    state.editProfile = false;
    state.editDraft = null;
    toast('Профиль сохранён');
    renderTab();
  } catch (e) { toast('Ошибка: ' + e.message); }
}

function numberOrNull(id) {
  const raw = $(id).value.trim();
  if (raw === '') return null;
  const n = Number(raw);
  return Number.isFinite(n) ? Math.round(n) : null;
}

/* ---------- Тесты кандидата ---------- */
function assignmentsView() {
  if (state.candidateListErrors.assignments) return emptyBox('⚠️', 'Не удалось загрузить тесты');
  const list = state.assignments;
  return `
  <div class="page-head"><h2>Тесты</h2><p>Назначенные проверочные задания</p></div>
  <div class="content">
    ${list.length ? list.map((a, i) => `
      <div class="row screen-enter" style="animation-delay:${i * 50}ms" onclick="openTest('${a.id}')">
        <div class="row-icon" style="background:${a.submitted ? 'var(--green-soft)' : 'var(--accent-soft)'}">${a.submitted ? '✅' : '📝'}</div>
        <div class="row-body">
          <div class="row-title">${esc(a.role || ('Вакансия ' + shortId(a.vacancyId)))}</div>
          <div class="row-sub">${a.submitted ? 'Отправлено' : 'Ожидает выполнения'}</div>
        </div>
        <div class="row-right"><span class="chip ${a.submitted ? 'chip-green' : 'chip-violet'}">${a.submitted ? 'ГОТОВО' : 'НОВОЕ'}</span></div>
      </div>`).join('') : emptyBox('🗂️', 'Тестов пока нет\nТам появятся назначенные задания')}
  </div>`;
}

async function openTest(id) {
  try {
    const data = await api('/api/v1/candidate/test-assignments/' + id);
    if (data.submitted) { renderTab(); return toast('Ответы уже отправлены'); }
    data.role = (state.assignments || []).find(a => a.id === id && a.role)?.role || data.role || 'вакансия';
    state.testSession = { assignment: data, questions: data.questions || [], answers: {}, deadline: null };
    render('test-screen', testScreen());
    showView('test');
    scrollViewTop('test');
  } catch (e) {
    toast('Ошибка: ' + e.message);
  }
}

function testScreen() {
  const s = state.testSession;
  const qs = s.questions || [];
  const role = esc(s.assignment.role || 'вакансия');
  const head = `
  <div class="test-head">
    <div class="test-head-inner">
      <div class="test-head-titles">
        <div class="test-head-title">Тест</div>
        <div class="test-head-sub">${role}</div>
      </div>
      <button class="btn-icon test-head-close" onclick="closeTest()" aria-label="Закрыть">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
      </button>
    </div>
  </div>`;
  if (!qs.length) return `
  ${head}
  <div class="content screen-enter">${emptyBox('📝', 'Тест ещё формируется\nВопросы скоро появятся')}</div>`;
  const answered = qs.filter(q => q.type === 'SINGLE_CHOICE'
    ? s.answers[q.questionId] !== undefined && s.answers[q.questionId] !== null
    : (s.answers[q.questionId] || '').toString().trim()).length;
  return `
  ${head}
  <div class="content screen-enter">
    <div class="test-progress"><span id="tp-label">Отвечено ${answered} из ${qs.length}</span><div class="tp-bar"><div class="tp-fill" id="tp-fill" style="width:${Math.round(answered / qs.length * 100)}%"></div></div></div>
    ${qs.map((q, i) => questionBlock(q, i)).join('')}
    <button class="btn btn-primary" style="margin-top:6px;margin-bottom:20px" onclick="submitTest()">Отправить ответы</button>
  </div>`;
}

function renderQText(text) {
  const src = esc(text || '');
  if (!/\x60\x60\x60/.test(src)) return `<div class="q-text">${src}</div>`;
  const parts = src.split(/\x60\x60\x60/g);
  let html = '';
  for (let i = 0; i < parts.length; i++) {
    if (i % 2 === 0) {
      if (parts[i]) html += parts[i];
    } else {
      const chunk = parts[i].replace(/^\n/, '');
      const m = chunk.match(/^(\S*)\n?/);
      const lang = m && m[1] ? ` data-lang="${m[1]}"` : '';
      const body = chunk.replace(/^\S*\n?/, '').replace(/\n$/, '');
      html += `<pre class="code-block"${lang}>${body || ' '}</pre>`;
    }
  }
  return `<div class="q-text">${html}</div>`;
}

function questionBlock(q, i) {
  if (q.type === 'SINGLE_CHOICE') {
    const sel = state.testSession.answers[q.questionId];
    return `
    <div class="card question screen-enter" style="animation-delay:${i * 40}ms">
      <div class="q-num">Вопрос ${i + 1}</div>
      ${renderQText(q.text)}
      ${(q.options || []).map((opt, oi) => `
        <button class="answer-opt ${sel === oi ? 'selected' : ''}" data-qid="${q.questionId}" data-idx="${oi}" onclick="pickAnswer('${q.questionId}', ${oi})">${esc(opt)}</button>`).join('')}
    </div>`;
  }
  return `
  <div class="card question screen-enter" style="animation-delay:${i * 40}ms">
    <div class="q-num">Вопрос ${i + 1}</div>
    ${renderQText(q.text)}
    <textarea class="input" id="answer-${esc(q.questionId)}" placeholder="Ваш ответ…" oninput="typeAnswer('${q.questionId}', this.value)">${esc(state.testSession.answers[q.questionId] || '')}</textarea>
  </div>`;
}

function pickAnswer(qid, optionIndex) {
  state.testSession.answers[qid] = optionIndex;
  document.querySelectorAll('.answer-opt[data-qid="' + CSS.escape(qid) + '"]')
    .forEach(btn => btn.classList.toggle('selected', Number(btn.dataset.idx) === optionIndex));
  updateTestProgress();
}

function typeAnswer(qid, value) {
  state.testSession.answers[qid] = value;
  updateTestProgress();
}

function updateTestProgress() {
  const s = state.testSession;
  if (!s) return;
  const qs = s.questions || [];
  const answered = qs.filter(q => q.type === 'SINGLE_CHOICE'
    ? s.answers[q.questionId] !== undefined && s.answers[q.questionId] !== null
    : (s.answers[q.questionId] || '').toString().trim()).length;
  const label = $('tp-label'), fill = $('tp-fill');
  if (label) label.textContent = 'Отвечено ' + answered + ' из ' + qs.length;
  if (fill) fill.style.width = Math.round(qs.length ? answered / qs.length * 100 : 0) + '%';
}

function closeTest() {
  state.testSession = null;
  showView('candidate');
  renderTab();
}

async function submitTest() {
  const s = state.testSession;
  const body = { answers: s.questions.map(q => {
    const val = s.answers[q.questionId];
    if (q.type === 'SINGLE_CHOICE') return { questionId: q.questionId, selectedOptionIndex: Number(val) };
    return { questionId: q.questionId, text: (val || '').trim() ? val : '' };
  }) };
  const unanswered = s.questions.some(q => q.type === 'SINGLE_CHOICE'
    ? s.answers[q.questionId] === undefined && s.answers[q.questionId] !== 0
    : !((s.answers[q.questionId] || '').toString().trim()));
  if (unanswered) return toast('Ответьте на все вопросы');
  try {
    await api('/api/v1/candidate/test-assignments/' + s.assignment.id + '/answers', { method: 'POST', body });
    state.testSession = null;
    await refreshCandidateLists();
    toast('Ответы отправлены ✓');
    state.tab = 'tests';
    showView('candidate');
    renderTab();
  } catch (e) { toast('Ошибка: ' + e.message); }
}

/* ---------- Приглашения кандидата ---------- */
function invitationsView() {
  if (state.candidateListErrors.invitations) return emptyBox('⚠️', 'Не удалось загрузить приглашения');
  const list = state.invitations;
  return `
  <div class="page-head"><h2>Приглашения</h2><p>Отклики и предложения работодателей</p></div>
  <div class="content">
    ${list.length ? list.map((inv, i) => `
      <div class="card screen-enter" style="animation-delay:${i * 50}ms">
        <div style="display:flex;align-items:center;gap:12px;margin-bottom:10px">
          <div class="row-icon" style="background:var(--accent-soft)">💌</div>
          <div style="flex:1"><div class="row-title">${esc(inv.role || ('Вакансия ' + shortId(inv.vacancyId)))}</div>
          <div class="row-sub">${inv.status === 'PENDING' ? 'Ожидает вашего решения' : 'Обработано'}</div></div>
          <span class="chip ${invStatusChip(inv.status)}">${invStatusText(inv.status)}</span>
        </div>
        ${inv.status === 'PENDING' ? `
        <div style="display:flex;gap:8px">
          <button class="btn btn-green-soft btn-sm" style="flex:1" onclick="decideInvitation('${inv.id}','ACCEPTED')">✓ Принять</button>
          <button class="btn btn-danger-soft btn-sm" style="flex:1" onclick="decideInvitation('${inv.id}','DECLINED')">✕ Отклонить</button>
        </div>` : ''}
      </div>`).join('') : emptyBox('💌', 'Приглашений пока нет')}
  </div>`;
}

function invStatusChip(status) {
  return { PENDING: 'chip-amber', ACCEPTED: 'chip-green', DECLINED: 'chip-gray' }[status] || 'chip-gray';
}
function invStatusText(status) {
  return { PENDING: 'В ожидании', ACCEPTED: 'Принято', DECLINED: 'Отклонено' }[status] || status;
}

async function decideInvitation(id, decision) {
  try {
    await api('/api/v1/candidate/invitations/' + id + '/decision', { method: 'PUT', body: { decision } });
    await refreshCandidateLists();
    toast(decision === 'ACCEPTED' ? 'Приглашение принято ✓' : 'Приглашение отклонено');
    renderTab();
  } catch (e) { toast('Ошибка: ' + e.message); }
}

/* ===================== ЛОГИКА НАНИМАТЕЛЯ ===================== */
async function initEmployer() {
  state.view = 'employer';
  state.tab = 'vacancies';
  renderHero('employer');
  await refreshVacancies();
  renderShell('employer');
  showView('employer');
  renderTab();
}

function renderHero(role) {
  const box = $((role === 'candidate' ? 'cand' : 'emp') + '-hero');
  const name = state.me?.id ? shortId(state.me.id) : 'гость';
  const id = state.me?.id || '';
  const isCand = role === 'candidate';
  box.className = 'hero ' + (isCand ? 'hero-cand' : 'hero-emp');
  box.innerHTML = `
    <div class="blob b1"></div><div class="blob b2"></div>
    <div class="hero-top">
      <div class="logo logo-onhero"><div class="logo-mark">S</div><div class="logo-name">Staff<b>Match</b></div></div>
      <span class="hero-chip">${isCand ? 'Кандидат' : 'Наниматель'}</span>
    </div>
    <div class="hero-greet">
      <div class="hero-avatar">${isCand ? '👤' : '🏢'}</div>
      <div class="hero-msg">
        <h1>${isCand ? 'Здравствуйте!' : 'Добро пожаловать!'}</h1>
        <p>${isCand ? 'Проходите тесты и получайте офферы' : 'Находите, тестируйте и сравнивайте кандидатов'}</p>
      </div>
    </div>
    <div class="hero-id">ID-профиль: <b>${esc(id)}</b></div>`;
}

async function refreshVacancies() {
  try { state.vacancies = await api('/api/v1/employer/vacancies'); } catch (_) { state.vacancies = []; }
}

/* ---------- Список вакансий ---------- */
function vacanciesView() {
  const list = state.vacancies;
  return `
  <div class="page-head"><h2>Вакансии</h2><p>${list.length} активн${plural(list.length, 'ая', 'ые', 'ых')}</p></div>
  <div class="content">
    ${list.map((v, i) => vacancyRow(v, i)).join('')}
    <button class="btn btn-primary" style="margin-top:6px" onclick="openCreateVacancy()">＋ Создать вакансию</button>
  </div>`;
}

function vacancyRow(v, i) {
  const ready = v.matchingStatus === 'READY' && (v.testMode === 'NONE' || v.generationStatus === 'READY');
  return `
  <div class="row screen-enter" style="animation-delay:${i * 50}ms" onclick="openVacancy('${v.id}')">
    <div class="row-icon" style="background:${ready ? 'var(--green-soft)' : 'var(--amber-soft)'}">${ready ? '✅' : '⏳'}</div>
    <div class="row-body">
      <div class="row-title">${esc(v.role)}</div>
      <div class="row-sub">${esc(v.city || 'Любой город')} · ${WORK_FORMATS[v.workFormat] || v.workFormat}${v.salaryFrom ? ' · от ' + money(v.salaryFrom) : ''}</div>
    </div>
    <div class="row-right"><span class="chip ${ready ? 'chip-green' : 'chip-amber'}">${ready ? 'ГОТОВА' : statusShort(v)}</span></div>
  </div>`;
}

function statusShort(v) {
  if (v.matchingStatus !== 'READY') return 'MATCHING';
  return v.testMode === 'AUTO' && v.generationStatus !== 'READY' ? 'ГЕНЕРАЦИЯ' : 'РАНГИРОВАНИЕ';
}

/* ---------- Создание вакансии ---------- */
const createForm = {
  skills: [],
  selectedWF: 'REMOTE',
  level: 'JUNIOR',
  testMode: 'AUTO',
  competencies: [],
  questions: [],
  editingQuestion: null
};

function openCreateVacancy() {
  state.vacancy = null;
  createForm.skills = [];
  createForm.competencies = [];
  createForm.questions = [];
  createForm.selectedWF = 'REMOTE';
  createForm.level = 'JUNIOR';
  createForm.testMode = 'AUTO';
  state.vacancyTab = 'create';
  state.view = 'vacancy';
  render('vacancy-screen', createVacancyForm());
  showView('vacancy');
}

function createVacancyForm() {
  return `
  <div class="test-head">
    <div class="test-head-inner">
      <div class="test-head-titles">
        <div class="test-head-title">Новая <b>вакансия</b></div>
        <div class="test-head-sub">Кого ищете?</div>
      </div>
      <button class="btn-icon test-head-close" onclick="backToVacancies()" aria-label="Закрыть">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round"><path d="M18 6 6 18M6 6l12 12"/></svg>
      </button>
    </div>
  </div>
  <div class="content screen-enter">
    <div class="card">
      <div class="field"><label>Должность <span class="req">*</span></label><input class="input" id="vac-role" placeholder="Менеджер по продажам, повар, водитель…"></div>
      <div class="field"><label>Город</label><input class="input" id="vac-city" placeholder="Москва"></div>
      <div class="field">
        <div class="label-hint"><label>Формат работы <span class="req">*</span></label></div>
        <div class="opt-row">
          ${Object.entries(WORK_FORMATS).map(([k, v]) => `<button type="button" class="opt ${createForm.selectedWF === k ? 'active' : ''}" data-wf="${k}" onclick="setCreateWF('${k}')">${v}</button>`).join('')}
        </div>
      </div>
      <div class="field"><label>Нагрузка (часов в неделю)</label><input class="input" id="vac-hours" type="number" min="0" max="168" placeholder="40"></div>
      <div style="display:flex;gap:10px">
        <div class="field" style="flex:1"><label>Зарплата от (₽)</label><input class="input" id="vac-from" type="number" min="0" placeholder="100000"></div>
        <div class="field" style="flex:1"><label>до (₽)</label><input class="input" id="vac-to" type="number" min="0" placeholder="180000"></div>
      </div>
      <div class="field"><label>Опыт (мес., минимум)</label><input class="input" id="vac-exp" type="number" min="0" placeholder="12"></div>
      <div class="field">
        <div class="label-hint"><label>Уровень</label></div>
        <div class="opt-row">
          ${LEVELS.map(l => `<button type="button" class="opt ${l === createForm.level ? 'active' : ''}" data-lvl="${l}" onclick="setVacLevel('${l}')">${l}</button>`).join('')}
        </div>
      </div>
    </div>

    <div class="card">
      <h3>Требуемые навыки <span class="req">*</span></h3>
      <div id="vac-skills">
        ${createForm.skills.map((s, i) => skillRow(s, i)).join('')}
      </div>
      <div class="skill-add">
        <input class="input" id="new-vac-skill" list="skill-codes" placeholder="Код навыка">
        <select class="input" id="new-vac-min"><option value="2">≥2</option><option value="3" selected>≥3</option><option value="4">≥4</option><option value="5">≥5</option></select>
        <button type="button" class="btn btn-primary" onclick="addVacSkill()" aria-label="Добавить навык">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="width:18px;height:18px"><path d="M12 5v14M5 12h14"/></svg>
        </button>
      </div>
    </div>

    <div class="card">
      <h3>Тестирование</h3>
      <div class="opt-row" style="margin-bottom:16px">
        ${Object.entries(TEST_MODES).map(([k, v]) => `<button type="button" class="opt ${createForm.testMode === k ? 'active' : ''}" data-mode="${k}" onclick="setTestMode('${k}')">${v}</button>`).join('')}
      </div>
      ${createForm.testMode === 'AUTO' ? `
      <div style="display:flex;gap:10px">
        <div class="field" style="flex:1"><label>Минут</label><input class="input" id="vac-minutes" type="number" min="5" max="10" value="7"></div>
        <div class="field" style="flex:1"><label>Вопросов (6–8)</label><input class="input" id="vac-questions" type="number" min="6" max="8" value="7"></div>
      </div>
      <div class="field">
        <div class="label-hint"><label>Компетенции (опционально)</label><span>код + вес</span></div>
        <div id="vac-comps">
          ${createForm.competencies.map((c, i) => `<span class="skill-chip">${esc(c.code)} w${c.weight} <button onclick="removeComp(${i})">✕</button></span>`).join('')}
        </div>
        <div class="skill-add" style="margin-top:10px">
          <input class="input" id="new-comp-code" list="skill-codes" placeholder="Компетенция">
          <input class="input" id="new-comp-weight" type="number" min="0" style="flex:0 0 84px" placeholder="вес">
          <button type="button" class="btn btn-primary" onclick="addComp()" aria-label="Добавить компетенцию">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" style="width:18px;height:18px"><path d="M12 5v14M5 12h14"/></svg>
          </button>
        </div>
      </div>` : ''}
      ${createForm.testMode === 'CUSTOM' ? `
      <div class="field">
        <div class="label-hint"><label>Минут</label></div>
        <input class="input" id="vac-minutes" type="number" min="5" max="10" value="7">
      </div>
      <h3 style="margin-bottom:10px">Свои вопросы</h3>
      <div id="custom-questions">${createForm.questions.map((q, i) => customQBlock(q, i)).join('')}</div>
      <button class="btn btn-ghost btn-sm" onclick="openQuestionModal()">＋ Добавить вопрос</button>` : ''}
    </div>

    <button class="btn btn-primary" style="margin-bottom:20px" onclick="submitVacancy()">🚀 Создать вакансию</button>
  </div>`;
}

function skillRow(s, i) {
  return `<span class="skill-chip">${esc(s.code)} (мин ${s.minLevel}, ${s.weight}%, ${s.required ? 'обяз' : 'опц'}) <button onclick="removeVacSkill(${i})">✕</button></span>`;
}

function rerenderCardField(id, html) {
  const el = $(id);
  if (el) el.innerHTML = html;
}

const VAC_FORM_INPUTS = ['vac-role', 'vac-city', 'vac-hours', 'vac-from', 'vac-to', 'vac-exp', 'vac-minutes', 'vac-questions'];

function rerenderVacancyForm() {
  const saved = {};
  VAC_FORM_INPUTS.forEach(id => { const el = $(id); if (el) saved[id] = el.value; });
  render('vacancy-screen', createVacancyForm());
  VAC_FORM_INPUTS.forEach(id => { const el = $(id); if (el && id in saved) el.value = saved[id]; });
  document.querySelectorAll('#vacancy-screen .screen-enter').forEach(el => el.classList.remove('screen-enter'));
}

function setVacLevel(l) {
  createForm.level = l;
  document.querySelectorAll('[data-lvl]').forEach(e => e.classList.toggle('active', e.dataset.lvl === l));
}

function setTestMode(mode) {
  createForm.testMode = mode;
  rerenderVacancyForm();
}

function setCreateWF(wf) {
  createForm.selectedWF = wf;
  document.querySelectorAll('[data-wf]').forEach(e => e.classList.toggle('active', e.dataset.wf === wf));
}

function addVacSkill() {
  const code = ($('new-vac-skill').value.trim().toUpperCase() || '').replace(/[^A-Z0-9_]/g, '');
  const minLevel = Number($('new-vac-min').value);
  if (!code) return toast('Введите код навыка');
  if (createForm.skills.find(s => s.code === code)) return toast('Навык уже добавлен');
  createForm.skills.push({ code, minLevel, required: true, weight: 100 });
  rerenderCardField('vac-skills', createForm.skills.map((s, i) => skillRow(s, i)).join(''));
  $('new-vac-skill').value = '';
}

function removeVacSkill(i) {
  createForm.skills.splice(i, 1);
  rerenderCardField('vac-skills', createForm.skills.map((s, i) => skillRow(s, i)).join(''));
}

function addComp() {
  const code = ($('new-comp-code').value.trim().toUpperCase() || '').replace(/[^A-Z0-9_]/g, '');
  const weight = Number($('new-comp-weight').value);
  if (!code) return toast('Введите код компетенции');
  createForm.competencies.push({ code, weight });
  rerenderCardField('vac-comps', createForm.competencies.map((c, i) => `<span class="skill-chip">${esc(c.code)} w${c.weight} <button onclick="removeComp(${i})">✕</button></span>`).join(''));
  $('new-comp-code').value = ''; $('new-comp-weight').value = '';
}

function removeComp(i) {
  createForm.competencies.splice(i, 1);
  rerenderCardField('vac-comps', createForm.competencies.map((c, i) => `<span class="skill-chip">${esc(c.code)} w${c.weight} <button onclick="removeComp(${i})">✕</button></span>`).join(''));
}

/* ---------- Кастомные вопросы ---------- */
function customQBlock(q, i) {
  return `
  <div class="cq">
    <div class="cq-head"><b>#${i + 1} · ${q.type === 'SINGLE_CHOICE' ? 'Выбор варианта' : 'Свободный ответ'}</b>
      <div style="display:flex;gap:6px">
        <button onclick="editCustomQ(${i})">✎</button>
        <button class="cq-del" onclick="removeCustomQ(${i})">✕</button>
      </div></div>
    <p style="font-size:14px;font-weight:700;margin-bottom:6px">${esc(q.text)}</p>
    <p class="soft" style="font-size:12.5px">${esc(q.competency)} · max ${q.maxScore} баллов${q.type === 'SINGLE_CHOICE' ? ' · ' + (q.options || []).length + ' вариантов, ответ №' + ((q.correctAnswer && q.correctAnswer.optionIndex) + 1) : ''}</p>
  </div>`;
}

function removeCustomQ(i) {
  createForm.questions.splice(i, 1);
  rerenderVacancyForm();
}

function editCustomQ(i) {
  createForm.editingQuestion = i;
  openModal(questionModal(i));
  renderQuestionType();
}

function openQuestionModal() {
  createForm.editingQuestion = null;
  openModal(questionModal(-1));
  renderQuestionType();
}

function questionModal(index) {
  const q = index >= 0 ? createForm.questions[index] : null;
  return `
  <h3>${q ? 'Редактировать' : 'Новый вопрос'}</h3>
  <div class="modal-sub">Вопрос добавляется в тест вакансии</div>
  <div class="field"><label>Тип</label>
    <select class="input" id="q-type" onchange="renderQuestionType()" onclick="">
      <option value="SINGLE_CHOICE" ${q?.type === 'SINGLE_CHOICE' ? 'selected' : ''}>Выбор варианта</option>
      <option value="FREE_TEXT" ${q?.type === 'FREE_TEXT' ? 'selected' : ''}>Свободный ответ</option>
    </select>
  </div>
  <div class="field"><label>Компетенция <span class="req">*</span></label>
    <input class="input" id="q-comp" list="skill-codes" value="${esc(q?.competency || '')}" placeholder="Например, ACCOUNTING"></div>
  <div class="field"><label>Текст <span class="req">*</span></label>
    <textarea class="input" id="q-text" placeholder="Вопрос…">${esc(q?.text || '')}</textarea></div>
  <div class="field"><label>Max баллов (1–100)</label>
    <input class="input" id="q-max" type="number" min="1" max="100" value="${esc(q?.maxScore ?? 10)}"></div>
  <div id="q-extra"></div>
  <div style="display:flex;gap:8px;margin-top:8px">
    <button class="btn btn-ghost" onclick="closeModal()">Отмена</button>
    <button class="btn btn-primary" onclick="saveQuestion(${index})">Сохранить</button>
  </div>`;
}

function renderQuestionType() {
  const type = $('q-type').value;
  const isChoice = type === 'SINGLE_CHOICE';
  const wrap = $('q-extra');
  const q = createForm.editingQuestion != null ? createForm.questions[createForm.editingQuestion] : null;
  if (isChoice) {
    wrap.innerHTML = `
      <div class="field"><label>Варианты ответа (мин 2)</label>
        <div id="q-opt-list">${choiceOptionsHTML(q?.options || ['', ''])}</div>
        <button type="button" class="btn btn-ghost btn-sm" onclick="addOptionInput()">＋ Вариант</button>
      </div>
      <div class="field"><label>Правильный вариант (индекс)</label>
        <input class="input" id="q-correct" type="number" min="0" value="${esc(q?.correctAnswer?.optionIndex ?? 0)}"></div>`;
  } else {
    wrap.innerHTML = `
      <div class="field"><div class="label-hint"><label>Критерии оценки</label><span>по строке на критерий</span></div>
        <textarea class="input" id="q-criteria" placeholder="Аргументированность ответа&#10;Знание технологии&#10;…">${esc((q?.rubric?.criteria || []).join('\n'))}</textarea></div>`;
  }
}

function choiceOptionsHTML(opts) {
  return opts.map((o, i) => `
    <div class="opt-list-row">
      <input class="input" id="q-opt-${i}" value="${esc(o)}" placeholder="Вариант ${i + 1}">
      <button type="button" class="opt-list-del" onclick="removeOptionInput(${i})" aria-label="Удалить вариант">✕</button>
    </div>`).join('');
}

function addOptionInput() {
  const list = document.getElementById('q-opt-list');
  if (!list) return;
  addOptionRow(list.querySelectorAll('.opt-list-row').length);
}

function addOptionRow(i) {
  const list = document.getElementById('q-opt-list');
  if (!list) return;
  const row = document.createElement('div');
  row.className = 'opt-list-row';
  row.innerHTML = `<input class="input" id="q-opt-${i}" placeholder="Вариант ${i + 1}">
    <button type="button" class="opt-list-del" onclick="removeOptionInput(${i})" aria-label="Удалить вариант">✕</button>`;
  list.appendChild(row);
}

function removeOptionInput(i) {
  const list = document.getElementById('q-opt-list');
  if (!list) return;
  const rows = Array.from(list.querySelectorAll('.opt-list-row'));
  if (rows.length <= 2) return toast('Нужно минимум 2 варианта');
  const vals = rows.map(r => r.querySelector('input').value).filter((_v, j) => j !== i);
  list.innerHTML = choiceOptionsHTML(vals);
}

function saveQuestion(index) {
  const type = $('q-type').value;
  const competency = $('q-comp').value.trim();
  const text = $('q-text').value.trim();
  const maxScore = Number($('q-max').value);
  if (!competency) return toast('Укажите компетенцию');
  if (!text) return toast('Введите текст вопроса');

  if (type === 'SINGLE_CHOICE') {
    const options = [];
    document.querySelectorAll('#q-opt-list input').forEach(o => { if (o.value.trim()) options.push(o.value.trim()); });
    const correctAnswer = { optionIndex: Number($('q-correct').value || 0) };
    if (options.length < 2) return toast('Нужно минимум 2 варианта');
    if (correctAnswer.optionIndex < 0 || correctAnswer.optionIndex >= options.length) return toast('Неверный индекс правильного ответа');
    const question = { type, competency, text, options, correctAnswer, maxScore: maxScore || 10 };
    if (index >= 0) createForm.questions[index] = question; else createForm.questions.push(question);
  } else {
    const criteria = $('q-criteria').value.split('\n').map(s => s.trim()).filter(Boolean);
    if (!criteria.length) return toast('Добавьте критерии оценки');
    const rubric = { criteria, maxScore: maxScore || 10 };
    const question = { type, competency, text, rubric, maxScore: maxScore || 10 };
    if (index >= 0) createForm.questions[index] = question; else createForm.questions.push(question);
  }
  closeModal();
  rerenderVacancyForm();
}

/* ---------- Публикация вакансии ---------- */
async function submitVacancy() {
  const role = $('vac-role').value.trim();
  const city = $('vac-city').value.trim();
  const hoursMin = numberOrNull('vac-hours');
  const salaryFrom = numberOrNull('vac-from');
  const salaryTo = numberOrNull('vac-to');
  const experienceMonthsMin = numberOrNull('vac-exp');
  const level = createForm.level;

  if (!role) return toast('Укажите должность');
  if (!createForm.skills.length) return toast('Добавьте требуемые навыки');

  const body = {
    role, city: city || null, workFormat: createForm.selectedWF,
    hoursMin, salaryFrom, salaryTo, experienceMonthsMin,
    skills: createForm.skills,
    testMode: createForm.testMode, level
  };
  if (createForm.testMode === 'AUTO') {
    body.durationMinutes = Number($('vac-minutes')?.value ?? 7);
    body.questionCount = Number($('vac-questions')?.value ?? 7);
    if (createForm.competencies.length) body.competencies = createForm.competencies;
  }
  if (createForm.testMode === 'CUSTOM') {
    body.durationMinutes = Number($('vac-minutes')?.value ?? 7);
    if (!createForm.questions.length) return toast('Добавьте хотя бы один вопрос теста');
    body.customQuestions = createForm.questions;
  }
  try {
    const created = await api('/api/v1/employer/vacancies', { method: 'POST', body });
    state.vacancy = created;
    await refreshVacancies();
    toast('Вакансия создана');
    await openVacancy(created.id);
  } catch (e) { toast('Ошибка: ' + e.message); }
}

/* ---------- Детали вакансии ---------- */
async function openVacancy(id) {
  state.vacancyTab = 'overview';
  state.view = 'vacancy';
  render('vacancy-screen');
  showView('vacancy');
  try {
    state.vacancy = await api('/api/v1/employer/vacancies/' + id);
    try { state.matches = await api('/api/v1/employer/vacancies/' + id + '/matches'); } catch (_) { state.matches = []; }
    try { state.ranking = await api('/api/v1/employer/vacancies/' + id + '/ranking'); } catch (_) { state.ranking = null; }
    try { state.results = await api('/api/v1/employer/vacancies/' + id + '/results'); } catch (_) { state.results = []; }
    if (state.vacancy.testMode !== 'NONE') { try { state.vacancyTests = await api('/api/v1/employer/vacancies/' + id + '/test'); } catch (_) { state.vacancyTests = null; } }
    render('vacancy-screen', vacancyDetail());
  } catch (e) {
    toast('Ошибка: ' + e.message);
    backToVacancies();
  }
}

function vacancyBody() {
  const v = state.vacancy;
  if (!v) return '';
  if (state.vacancyTab === 'overview') return overviewTab(v);
  if (state.vacancyTab === 'matches') return matchesTab();
  if (state.vacancyTab === 'ranking') return rankingTab();
  return resultsTab();
}

function vacancyDetail() {
  const v = state.vacancy;
  if (!v) return '';
  const tabs = [['overview', 'Обзор'], ['matches', 'Кандидаты'], ['ranking', 'Рейтинг'], ['results', 'Результаты']];
  return `
  <div class="hero hero-mini">
    <div class="blob b1"></div><div class="blob b2"></div>
    <div style="display:flex;align-items:center;justify-content:space-between">
      <button class="btn-back" onclick="backToVacancies()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
      </button>
      <span class="hero-chip">Вакансия</span>
    </div>
    <div class="hero-greet">
      <h1>${esc(v.role)}</h1>
      <p>${esc(v.city || 'Любой город')} · ${WORK_FORMATS[v.workFormat] || v.workFormat}${v.salaryFrom ? ' · от ' + money(v.salaryFrom) : ''}</p>
    </div>
  </div>
  <div class="content">
    <div class="seg">
      ${tabs.map(([t, label]) => `<button class="${state.vacancyTab === t ? 'active' : ''}" onclick="setVacancyTab('${t}')">${label}</button>`).join('')}
    </div>
    <div class="tabbody screen-enter" id="vacancy-body">${vacancyBody()}</div>
  </div>`;
}

function setVacancyTab(t) {
  state.vacancyTab = t;
  const seg = document.querySelector('#vacancy-screen .seg');
  if (seg) {
    const labels = ['overview', 'matches', 'ranking', 'results'];
    [...seg.children].forEach((b, i) => b.classList.toggle('active', labels[i] === t));
  }
  const body = $('vacancy-body');
  if (body) {
    body.classList.remove('screen-enter');
    void body.offsetWidth;
    body.innerHTML = vacancyBody();
    body.classList.add('screen-enter');
    return;
  }
  renderTab();
}

function overviewTab(v) {
  const ready = v.matchingStatus === 'READY' && (v.testMode === 'NONE' || v.generationStatus === 'READY' && v.rankingStatus === 'READY');
  return `
  <div class="card screen-enter">
    <h3>Статус обработки</h3>
    ${statusLine('Матчинг', v.matchingStatus, v.matchingError, 'POST /api/v1/employer/vacancies/' + v.id + '/matching/recalculate', 'Пересчитать')}
    ${v.testMode !== 'NONE' ? statusLine('Тест', v.generationStatus, v.generationError, v.generationStatus === 'FAILED' ? 'POST /api/v1/employer/vacancies/' + v.id + '/test/generate/retry' : null, 'Повторить') : ''}
    ${statusLine('Ранжирование', v.rankingStatus, v.rankingError, 'POST /api/v1/employer/vacancies/' + v.id + '/ranking/recalculate', 'Пересчитать')}
  </div>
  <div class="card screen-enter">
    <h3>Тест</h3>
    <div class="muted" style="font-size:14px">
      Тест: <b>${TEST_MODES[v.testMode] || v.testMode}</b><br>
      Уровень: ${esc(v.level || '—')}${state.vacancyTests ? '<br>Вопросов: ' + state.vacancyTests.questions.length : ''}
    </div>
    ${v.testMode === 'NONE' ? '' : `
    <button class="btn btn-ghost btn-block" style="margin-top:12px" onclick="showTestQuestions()">Показать вопросы и ответы</button>`}
  </div>
  <div class="card screen-enter">
    <h3>Требования</h3>
    <div style="display:flex;flex-wrap:wrap;gap:8px">
      ${(v.skills || []).map(s => `<span class="skill-chip">${esc(s.code)} ≥${s.minLevel} ${s.required ? '' : '(опц)'}</span>`).join('') || '—'}
    </div>
    <div class="muted" style="font-size:13.5px;margin-top:12px">Опыт: ${v.experienceMonthsMin ?? 0} мес. · Нагрузка: ${v.hoursMin ?? '—'} ч/нед</div>
  </div>`;
}

function statusLine(name, status, error, action, actionLabel) {
  const map = { READY: ['chip-green', 'Готово'], RUNNING: ['chip-amber', 'В процессе'], FAILED: ['chip-red', 'Ошибка'], NONE: ['chip-gray', '—'] };
  const [cls, label] = map[status] || ['chip-gray', status];
  return `
  <div style="display:flex;align-items:center;gap:10px;margin-bottom:12px">
    <div style="flex:1"><b style="font-size:14px">${name}</b><br>
      ${error ? `<span class="soft" style="font-size:12px">${esc(error)}</span>` : ''}</div>
    <span class="chip ${cls}">${label}</span>
    ${action && status === 'FAILED' ? `<button class="btn btn-primary btn-sm" onclick="runAction('${action}')">${actionLabel}</button>` : ''}
  </div>`;
}

async function runAction(spec) {
  const sp = spec.indexOf(' ');
  const method = sp > 0 ? spec.slice(0, sp) : 'POST';
  const url = sp > 0 ? spec.slice(sp + 1) : spec;
  try {
    const res = await api(url, { method });
    if (res && res.id && state.vacancyTab === 'overview') state.vacancy = res;
    toast('Выполнено');
    await openVacancy(state.vacancy.id);
  } catch (e) { toast('Ошибка: ' + e.message); }
}

/* ---------- Поиск сотрудников (matches) ---------- */
function matchesTab() {
  const list = state.matches || [];
  return `
    ${list.length ? list.map((m, i) => candidateMatchCard(m, i)).join('') : emptyBox('🔍', 'Матрица пуста\nОпубликуйте вакансию, чтобы матчинг нашёл кандидатов')}`;
}

function candidateMatchCard(m, i) {
  const r = Math.max(0, Math.min(100, m.score || 0));
  const color = r >= 70 ? '#22c55e' : r >= 40 ? '#f59e0b' : '#ef4444';
  return `
  <div class="card candidate screen-enter" style="animation-delay:${i * 40}ms">
    <div class="cand-top">
      <div class="score-ring" style="background:${color}">${Math.round(r)}</div>
      <div style="flex:1">
        <div class="row-title">Кандидат ${shortId(m.candidateId)}</div>
        <div class="row-sub">${m.eligible ? 'Проходит по требованиям' : 'Частично подходит'}</div>
      </div>
      <span class="chip ${m.eligible ? 'chip-green' : 'chip-amber'}">${m.eligible ? 'MATCH' : 'PARTIAL'}</span>
    </div>
    <div class="match-badges">
      ${(m.matched || []).map(x => `<span class="mb mb-hit">✓ ${esc(x.code || x)}</span>`).join('')}
      ${(m.partial || []).map(x => `<span class="mb mb-part">◐ ${esc(x.code || x)}</span>`).join('')}
      ${(m.missing || []).map(x => `<span class="mb mb-miss">✕ ${esc(x.code || x)}</span>`).join('')}
    </div>
    <div class="cand-actions">
      <button class="btn btn-primary btn-sm" onclick="assignTest('${m.candidateId}')">📝 Тест</button>
      ${state.ranking ? `<button class="btn btn-green-soft btn-sm" onclick="inviteCandidate('${m.candidateId}')">✉️ Пригласить</button>` : ''}
    </div>
  </div>`;
}

async function assignTest(candidateId) {
  try {
    await api('/api/v1/employer/vacancies/' + state.vacancy.id + '/assignments', { method: 'POST', body: { candidateId } });
    toast('Тест отправлен кандидату ✓');
  } catch (e) { toast('Ошибка: ' + e.message); }
}

async function inviteCandidate(candidateId) {
  try {
    await api('/api/v1/employer/vacancies/' + state.vacancy.id + '/invitations', { method: 'POST', body: { candidateId } });
    toast('Приглашение отправлено ✓');
  } catch (e) { toast('Ошибка: ' + e.message); }
}

async function retryScore(assignmentId) {
  try {
    await api('/api/v1/employer/vacancies/' + state.vacancy.id + '/assignments/' + assignmentId + '/score/retry', { method: 'POST' });
    toast('Запущен пересчёт оценки');
    await openVacancy(state.vacancy.id);
  } catch (e) { toast('Ошибка: ' + e.message); }
}

/* ---------- Рейтинг ---------- */
function rankingTab() {
  const r = state.ranking;
  if (!r) return emptyBox('📊', 'Рейтинг формируется…');
  const final = r.final || [], waiting = r.waiting || [];
  return `
  <div style="margin-bottom:6px" class="screen-enter">
    <span class="chip chip-green">Финальные рекомендации</span>
  </div>
  ${final.length ? final.map((f, i) => `
    <div class="row screen-enter" style="animation-delay:${i * 40}ms">
      <div class="score-ring" style="width:40px;height:40px;font-size:13px;background:#6366f1">#${f.rank}</div>
      <div class="row-body">
        <div class="row-title">Кандидат ${shortId(f.candidateId)}</div>
        <div class="row-sub">Итоговый балл: ${Math.round(f.finalScore)} · матчинг+тест</div>
      </div>
      <div class="row-right"><span class="chip chip-green">${esc(f.state)}</span></div>
    </div>`).join('') : ''}
  ${waiting.length ? `
  <div style="margin:14px 0 6px" class="screen-enter"><span class="chip chip-amber">Ожидают тест</span></div>
  ${waiting.map((w, i) => `
    <div class="row screen-enter" style="animation-delay:${i * 40}ms">
      <div class="row-icon" style="background:var(--amber-soft)">⏳</div>
      <div class="row-body">
        <div class="row-title">Кандидат ${shortId(w.candidateId)}</div>
        <div class="row-sub">Матчинг: ${Number.isFinite(w.components?.matching) ? Math.round(w.components.matching) : '—'} · ждёт выполнение теста</div>
      </div>
    </div>`).join('')}` : ''}
  ${!final.length && !waiting.length ? emptyBox('📊', 'Кандидатов в рейтинге нет') : ''}`;
}

/* ---------- Результаты ---------- */
function resultsTab() {
  const list = state.results || [];
  return `
  ${list.length ? list.map((a, i) => resultRow(a, i)).join('') : emptyBox('📈', 'Результатов пока нет\nОтправьте тест кандидату')}`;
}

function resultRow(a, i) {
  const r = a.result;
  if (!r && a.submitted && a.status === 'SCORING_FAILED') return `
    <div class="row screen-enter">
      <div class="row-icon" style="background:var(--red-soft)">⚠️</div>
      <div class="row-body">
        <div class="row-title">Кандидат ${shortId(a.candidateId)}</div>
        <div class="row-sub">Оценка не удалась</div>
      </div>
      <button class="btn btn-primary btn-sm" onclick="retryScore('${a.id}')">↻ Пересчитать</button>
    </div>`;
  if (!r && a.submitted) return `<div class="row"><div class="row-body"><div class="row-title">Кандидат ${shortId(a.candidateId)}</div><div class="row-sub">Ответы отправлены, оценка готовится…</div></div></div>`;
  if (!r) return `<div class="row"><div class="row-body"><div class="row-title">Кандидат ${shortId(a.candidateId)}</div><div class="row-sub">Тест ещё не выполнен</div></div><span class="chip chip-amber">${a.status}</span></div>`;
  const pct = Math.round(r.totalScore);
  return `
  <div class="card screen-enter" style="animation-delay:${i * 40}ms">
    <div style="display:flex;align-items:center;gap:12px">
      <div class="score-ring" style="background:${pct >= 70 ? '#22c55e' : pct >= 40 ? '#f59e0b' : '#ef4444'}">${pct}%</div>
      <div style="flex:1"><div class="row-title">Кандидат ${shortId(a.candidateId)}</div>
        <div class="row-sub">Всего баллов: ${r.totalScore} · ${r.scoringVersion}</div></div>
    </div>
    ${r.breakdown && r.breakdown.length ? `
    <div style="margin-top:12px">
      ${r.breakdown.map(b => `
        <div style="display:flex;align-items:center;gap:8px;margin-bottom:6px">
          <span class="soft" style="font-size:12.5px;flex:1">${esc(b.competency)}</span>
          <div style="flex:2;height:8px;background:#eef0f6;border-radius:4px;overflow:hidden">
            <div style="height:100%;width:${b.percent}%;background:#6366f1;border-radius:4px"></div></div>
          <span class="muted" style="font-size:12.5px;width:54px;text-align:right">${b.score}/${b.maxScore}</span>
        </div>`).join('')}
    </div>` : ''}
  </div>`;
}

function showTestQuestions() {
  if (!state.vacancyTests) return toast('Тест пока не готов');
  openModal(`
    <h3>Вопросы теста</h3>
    <div class="modal-sub">С ответами для нанимателя</div>
    <div style="margin-bottom:16px">
    ${state.vacancyTests.questions.map((q, i) => `
      <div class="cq">
        <div class="q-num">Вопрос ${i + 1} · ${q.type === 'SINGLE_CHOICE' ? 'выбор' : 'текст'}</div>
        ${renderQText(q.text)}
        ${q.options ? `<div style="font-size:13px;color:var(--text-muted);margin-top:6px">${q.options.map((o, oi) => `<div>${oi === q.correctAnswer?.optionIndex ? '✅' : '•'} ${esc(o)}</div>`).join('')}</div>` : ''}
        ${q.rubric ? `<div class="soft" style="font-size:12.5px;margin-top:6px">Критерии: ${esc(String((q.rubric.criteria || []).join('; ')))}</div>` : ''}
        <div class="muted" style="font-size:12.5px;margin-top:6px">Max: ${q.maxScore} баллов · ${esc(q.competency)}</div>
      </div>`).join('')}
    </div>`);
}

/* ===================== РЕНДЕР ===================== */
const views = ['boot', 'connect', 'role', 'candidate', 'employer', 'vacancy', 'test'];

function showView(name) {
  views.forEach(v => $(('view-' + v)).classList.toggle('active', v === name));
  const tabbar = $('tabbar');
  if (tabbar) tabbar.classList.toggle('hidden', name !== 'candidate' && name !== 'employer');
}

function render(id, html) {
  const el = $(id);
  if (html !== undefined) el.innerHTML = html;
  const comps = el.querySelectorAll('[data-component]');
  comps.forEach(c => c.innerHTML = window[c.dataset.component] ? window[c.dataset.component]() : '');
}

function renderTab() {
  if (state.view === 'candidate') {
    if (state.tab === 'profile') render('cand-tab', profileForm());
    else if (state.tab === 'tests') render('cand-tab', assignmentsView());
    else render('cand-tab', invitationsView());
  } else if (state.view === 'employer') {
    if (state.tab === 'vacancies') render('emp-tab', vacanciesView());
    else render('emp-tab', employerProfileView());
  } else if (state.view === 'vacancy') {
    if (state.vacancy) render('vacancy-screen', vacancyDetail());
  }
}

function emptyBox(icon, text) {
  return `<div class="empty-box"><span class="big">${icon}</span>${text.replace(/\n/g, '<br>')}</div>`;
}

/* ---------- Каркас ---------- */
function renderShell(role) {
  const tabbar = $('tabbar');
  const tabs = role === 'candidate'
    ? [['profile', 'Профиль'], ['tests', 'Тесты', 'assignments'], ['invitations', 'Приглашения', 'invitations']]
    : [['vacancies', 'Вакансии'], ['profile', 'Профиль']];
  tabbar.innerHTML = tabs.map(([t, label, countKey]) => `
    <button class="tab ${state.tab === t ? 'active' : ''}" data-tab="${t}" onclick="switchTab('${t}')">
      <span class="tab-ico">${tabIcon(t)}</span>${label}${countKey ? `<span class="tab-count ${tabCount(countKey) ? '' : 'hidden'}">${tabCount(countKey)}</span>` : ''}
    </button>`).join('') + (isDemoMode() ? '<button class="tab" onclick="exitDemo()"><span class="tab-ico">↩</span>Выйти из демо</button>' : '');
}

function tabIcon(t) {
  const ic = {
    profile: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>',
    tests: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>',
    invitations: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><path d="m22 6-10 7L2 6"/></svg>',
    vacancies: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect width="20" height="14" x="2" y="7" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>'
  }[t];
  return ic || '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/></svg>';
}

function tabCount(kind) {
  if (kind === 'assignments') return (state.assignments || []).filter(a => !a.submitted).length;
  if (kind === 'invitations') return (state.invitations || []).filter(i => i.status === 'PENDING').length;
  return 0;
}

async function switchTab(t) {
  state.tab = t;
  state.editProfile = false;
  if (state.view === 'candidate' && (t === 'tests' || t === 'invitations')) {
    renderShell('candidate');
    render('cand-tab', emptyBox('⏳', 'Загрузка…'));
    try { await refreshCandidateLists(); }
    catch (e) { if (e.status === 401) return; }
  }
  renderShell(state.view === 'employer' ? 'employer' : 'candidate');
  renderTab();
  const view = document.getElementById('view-' + state.view);
  if (view) view.scrollTop = 0;
}

/* ---------- Профиль нанимателя ---------- */
function employerProfileView() {
  return `
  <div class="page-head"><h2>Профиль</h2><p>Компания-наниматель</p></div>
  <div class="content">
    <div class="card">
      <div style="display:flex;align-items:center;gap:14px">
        <div class="avatar-long" style="background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff">🏢</div>
        <div>
          <div class="row-title">Мой аккаунт</div>
          <div class="row-sub">ID: ${esc(state.me?.id || '—')}</div>
        </div>
        <span class="chip chip-violet">EMPLOYER</span>
      </div>
    </div>
    <div class="card">
      <div class="c-t"><h3>Статистика</h3></div>
      <div class="stats-grid">
        <div class="stat s-money">
          <div class="s-num">${state.vacancies.length}</div>
          <div class="s-lbl">Вакансий</div>
        </div>
        <div class="stat s-exp">
          <div class="s-num">${state.vacancies.filter(v => v.matchingStatus === 'READY').length}</div>
          <div class="s-lbl">Готовы</div>
        </div>
      </div>
    </div>
  </div>`;
}

/* ---------- Модал ---------- */
function openModal(html) {
  closeModal();
  const bg = document.createElement('div');
  bg.className = 'modal-bg bottom-sheet';
  bg.id = 'modal-bg';
  bg.innerHTML = `<div class="modal screen-enter">${html}</div>`;
  bg.addEventListener('click', e => { if (e.target === bg) closeModal(); });
  document.body.appendChild(bg);
}

function closeModal() {
  const m = $('modal-bg');
  if (m) m.remove();
}

/* ---------- Экран подключения ---------- */
function connectScreen() {
  return `
  <div class="connect-wrap connect screen-enter">
    <div class="connect-mark">S</div>
    <h1>Staff<b style="color:var(--accent)">Match</b></h1>
    <p class="connect-tagline">Найдём идеального сотрудника или вакансию</p>
    <p class="connect-sub">Откройте приложение внутри MAX-веб-версии, чтобы initData подставился автоматически. Для отладки в обычном браузере можно указать initData вручную.</p>
    <div class="connect-form">
      <div class="field"><label>MAX initData</label>
        <textarea class="input" id="login-initdata" rows="4" placeholder='user={...}&auth_date=...&hash=...'></textarea></div>
      <button class="btn btn-primary" onclick="connectFromManual()">Подключиться →</button>
      <button class="btn btn-ghost" onclick="toggleDevGen()">⚙️ Сгенерировать для теста</button>
      <button class="btn btn-demo" onclick="enterDemo()">👀 Посмотреть демо-версию</button>
    </div>
    <div id="dev-gen" class="hidden" style="width:100%;margin-top:14px;text-align:left">
      <div class="field"><label>MAX_BOT_TOKEN</label><input class="input" id="dev-token" placeholder="test-bot-token"></div>
      <div class="field"><label>Пользователь ID</label><input class="input" id="dev-user" type="number" value="101"></div>
      <button class="btn btn-ghost" onclick="genDev()">Сгенерировать</button>
    </div>
    <div class="soft" id="boot-error" style="font-size:13px;margin-top:16px;color:var(--red)" hidden></div>
  </div>`;
}

function toggleDevGen() {
  $('dev-gen').classList.toggle('hidden');
}

function connectFromManual() {
  const init = $('login-initdata').value.trim();
  if (!init) return toast('Вставьте initData');
  setInitData(init);
  boot();
}

async function genDev() {
  const token = $('dev-token').value.trim();
  const userId = Number($('dev-user').value);
  if (!token) return toast('Укажите бот-токен');
  try {
    const init = await generateInitData(token, userId);
    $('login-initdata').value = init;
    setInitData(init);
    toast('Сгенерировано');
    boot();
  } catch (e) {
    toast('Ошибка генерации: ' + e.message);
  }
}

/* ---------- Экран роли ---------- */
function roleScreen() {
  return `
  <div class="topbar">
    <div class="logo"><div class="logo-mark">S</div><div class="logo-name">Staff<b>Match</b></div></div>
  </div>
  <div class="content screen-enter">
    <div class="page-head" style="padding:8px 0 14px">
      <h2 style="font-size:25px">Кем вы хотите быть?</h2>
      <p>Выбор роли можно сделать один раз</p>
    </div>
    ${SUBCATS.map(([title, desc, role], i) => `
      <div class="card" style="cursor:pointer;${state.pendingRole === role ? 'opacity:.5' : ''}" onclick="rolePick('${role}')">
        <div style="display:flex;align-items:center;gap:14px">
          <div class="row-icon" style="background:${role === 'company' ? 'var(--accent-soft)' : 'var(--green-soft)'};font-size:26px">${role === 'company' ? '🏢' : '💼'}</div>
          <div style="flex:1">
            <div class="row-title" style="font-size:17px">${title}</div>
            <div class="row-sub" style="font-size:13.5px">${desc}</div>
          </div>
          ${state.pendingRole === role ? '<div class="spinner"></div>' : '<span style="color:var(--text-soft)">›</span>'}
        </div>
      </div>`).join('')}
    ${isDemoMode() ? '<button class="btn btn-ghost btn-block" onclick="exitDemo()">Выйти из демо</button>' : ''}
  </div>`;
}

async function rolePick(role) {
  const map = { company: 'EMPLOYER', candidate: 'CANDIDATE' };
  await chooseRole(map[role]);
}

/* ===================== ИНИЦИАЛИЗАЦИЯ ===================== */
window.addEventListener('DOMContentLoaded', () => {
  const root = document.getElementById('root');
  root.innerHTML = `
    <div class="view" id="view-boot">
      <div class="boot-wrap">
        <div class="boot-mark">S</div>
        <div class="boot-logo">Staff<b>Match</b></div>
        <div class="boot-sub">Найм и тесты кандидатов</div>
        <div class="spinner"></div>
      </div>
    </div>
    <div class="view" id="view-connect"></div>
    <div class="view" id="view-role"><div id="role-screen"></div></div>
    <div class="view" id="view-candidate">
      <div class="hero" id="cand-hero"></div>
      <div id="cand-tab"></div>
    </div>
    <div class="view" id="view-employer">
      <div class="hero" id="emp-hero"></div>
      <div id="emp-tab"></div>
    </div>
    <div class="view" id="view-vacancy"><div id="vacancy-screen"></div></div>
    <div class="view" id="view-test"><div id="test-screen"></div></div>
    <div class="tabbar hidden" id="tabbar"></div>`;
  render('view-connect', connectScreen());
  document.addEventListener('WebAppInitData', () => boot());
  boot();
});

// для onclick-глобальных вызовов
window.chooseRole = chooseRole;
window.rolePick = rolePick;
window.connectFromManual = connectFromManual;
window.toggleDevGen = toggleDevGen;
window.genDev = genDev;
window.switchTab = switchTab;
window.toggleEditProfile = toggleEditProfile;
window.cancelEditProfile = cancelEditProfile;
window.toggleWF = toggleWF;
window.addCandSkill = addCandSkill;
window.removeCandSkill = removeCandSkill;
window.saveCandidateProfile = saveCandidateProfile;
window.openTest = openTest;
window.closeTest = closeTest;
window.pickAnswer = pickAnswer;
window.typeAnswer = typeAnswer;
window.submitTest = submitTest;
window.decideInvitation = decideInvitation;
window.openCreateVacancy = openCreateVacancy;
window.submitVacancy = submitVacancy;
window.addVacSkill = addVacSkill;
window.removeVacSkill = removeVacSkill;
window.addComp = addComp;
window.removeComp = removeComp;
window.setCreateWF = setCreateWF;
window.setTestMode = setTestMode;
window.setVacLevel = setVacLevel;
window.openQuestionModal = openQuestionModal;
window.editCustomQ = editCustomQ;
window.removeCustomQ = removeCustomQ;
window.renderQuestionType = renderQuestionType;
window.saveQuestion = saveQuestion;
window.addOptionInput = addOptionInput;
window.openVacancy = openVacancy;
window.setVacancyTab = setVacancyTab;
window.runAction = runAction;
window.assignTest = assignTest;
window.inviteCandidate = inviteCandidate;
window.retryScore = retryScore;
window.showTestQuestions = showTestQuestions;
window.closeModal = closeModal;

/* Поток возврата к списку вакансий */
function backToVacancies() {
  state.vacancy = null;
  state.vacancyTab = 'overview';
  state.view = 'employer';
  state.tab = 'vacancies';
  renderShell('employer');
  render('emp-tab', vacanciesView());
  showView('employer');
  renderTab();
}

window.addEventListener('load', () => {
  // Задержка первичного появления кнопок
});
