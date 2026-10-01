/* Демо-режим StaffMatch: моковый API для просмотра дизайна без бэкенда.
   Включается кнопкой «Демо-данные» на экране подключения.
   Обходит api(): демонстрирует все экраны обеих ролей. */

const DEMO_KEY = 'fp_demo';

function isDemoMode() {
  return FP_CONFIG.demoMode || localStorage.getItem(DEMO_KEY) === '1';
}

function enterDemo() {
  localStorage.setItem(DEMO_KEY, '1');
  setInitData('demo-session');
  state.demo = true;
  state.me = { id: 987654321, role: null };
  toast('Демо-режим включён');
  try {
    showRoleSelect();
  } catch (e) {
    toast('Демо: ' + (e && e.message ? e.message : e));
  }
}

function exitDemo() {
  localStorage.removeItem(DEMO_KEY);
  if (localStorage.getItem(INIT_DATA_KEY) === 'demo-session') localStorage.removeItem(INIT_DATA_KEY);
  state.demo = false;
  boot();
}

window.enterDemo = enterDemo;
window.exitDemo = exitDemo;
window.isDemoMode = isDemoMode;

/* ---------------------- Моковые данные ---------------------- */

const DEMO_SKILLS = ['B2B_SALES', 'NEGOTIATION', 'CRM', 'ACCOUNTING', '1C', 'EXCEL', 'DRIVING_B', 'LOGISTICS', 'WAREHOUSE', 'COOKING'];

const demo = {
  role: null,
  candidate: {
    city: 'Москва',
    workFormats: ['REMOTE', 'HYBRID'],
    availableHours: 40,
    salaryExpectation: 350000,
    experienceMonths: 84,
    portfolioUrl: 'https://github.com/demo/dev-profile',
    skills: [
      { code: 'JAVA_CORE', level: 5 },
      { code: 'SPRING', level: 4 },
      { code: 'POSTGRESQL', level: 4 },
      { code: 'SQL', level: 4 }
    ]
  },
  invitations: [
    { id: 'inv-1', vacancyId: 'vac-14', role: 'Java-разработчик', status: 'PENDING' },
    { id: 'inv-2', vacancyId: 'vac-15', role: 'Backend-разработчик', status: 'DECLINED' }
  ],
  assignments: [
    {
      id: 'asn-1', vacancyId: 'vac-14', role: 'Java-разработчик', submitted: false,
      questions: [
        { questionId: 'q-j1', type: 'SINGLE_CHOICE', text: 'Что выведет код?\n\n```java\nint[] a = {1, 2, 3};\nSystem.out.println(a[2]);\n```', options: ['3', '1', 'Ошибку ArrayIndexOutOfBounds', 'null'] },
        { questionId: 'q-j2', type: 'SINGLE_CHOICE', text: 'Какой аннотацией Spring мапит GET-запрос?', options: ['@GetMapping', '@PostMapping', '@RequestMapping', '@Bean'] },
        { questionId: 'q-j3', type: 'FREE_TEXT', text: 'Опишите, чем отличаются ArrayList и LinkedList.' }
      ]
    },
    {
      id: 'asn-2', vacancyId: 'vac-15', role: 'Backend-разработчик', submitted: true,
      questions: [
        { questionId: 'q-s1', type: 'SINGLE_CHOICE', text: 'Что управляет бинами в Spring?', options: ['IoC-контейнер', 'JVM GC', 'Maven', 'WebSocket'] },
        { questionId: 'q-s2', type: 'SINGLE_CHOICE', text: 'Зачем нужен @Transactional?', options: ['Границы транзакций БД', 'Кэширование', 'Логирование', 'Валидация'] },
        { questionId: 'q-s3', type: 'FREE_TEXT', text: 'Как бы вы спроектировали REST API для каталога товаров?' }
      ]
    }
  ],
  assignmentQuestions: [
    { questionId: 'q-1', type: 'SINGLE_CHOICE', text: 'Что вы сделаете первым при работе с новым клиентом?', options: ['Выясните его потребности', 'Сразу предложите скидку', 'Расскажете о конкурентах', 'Перезвоните через неделю'] },
    { questionId: 'q-2', type: 'SINGLE_CHOICE', text: 'Главное правило приёмки товара на складе?', options: ['Сверить с накладной', 'Быстро убрать в зону хранения', 'Сразу заказать следующий товар', 'Заполнить путевой лист'] },
    { questionId: 'q-3', type: 'FREE_TEXT', text: 'Опишите, как вы построите работу с клиентской базой.' }
  ],
  vacancies: [
    {
      id: 'vac-11',
      role: 'Менеджер по продажам',
      city: 'Москва', workFormat: 'HYBRID',
      salaryFrom: 90000, salaryTo: 160000,
      experienceMonthsMin: 24, hoursMin: 40, level: 'MIDDLE',
      testMode: 'AUTO', matchingStatus: 'READY', generationStatus: 'READY', rankingStatus: 'READY',
      skills: [
        { code: 'B2B_SALES', minLevel: 3, required: true },
        { code: 'NEGOTIATION', minLevel: 3, required: true },
        { code: 'CRM', minLevel: 2, required: false }
      ]
    },
    {
      id: 'vac-12',
      role: 'Бухгалтер',
      city: 'Санкт-Петербург', workFormat: 'OFFICE',
      salaryFrom: 80000, salaryTo: 120000,
      experienceMonthsMin: 12, hoursMin: 40, level: 'JUNIOR',
      testMode: 'CUSTOM', matchingStatus: 'READY', generationStatus: 'READY', rankingStatus: 'READY',
      skills: [
        { code: 'ACCOUNTING', minLevel: 3, required: true },
        { code: '1C', minLevel: 2, required: true }
      ]
    },
    {
      id: 'vac-13',
      role: 'Логист-кладовщик',
      city: null, workFormat: 'OFFICE',
      salaryFrom: null, salaryTo: null,
      experienceMonthsMin: 48, hoursMin: 40, level: 'SENIOR',
      testMode: 'AUTO', matchingStatus: 'RUNNING', generationStatus: 'RUNNING', rankingStatus: 'NONE',
      skills: [
        { code: 'LOGISTICS', minLevel: 4, required: true },
        { code: 'WAREHOUSE', minLevel: 3, required: true }
      ]
    },
    {
      id: 'vac-14',
      role: 'Java-разработчик',
      city: 'Москва', workFormat: 'REMOTE',
      salaryFrom: 200000, salaryTo: 300000,
      experienceMonthsMin: 24, hoursMin: 40, level: 'MIDDLE',
      testMode: 'AUTO', matchingStatus: 'READY', generationStatus: 'READY', rankingStatus: 'READY',
      skills: [
        { code: 'JAVA_CORE', minLevel: 3, required: true },
        { code: 'SPRING', minLevel: 3, required: true },
        { code: 'POSTGRESQL', minLevel: 2, required: false }
      ]
    },
    {
      id: 'vac-15',
      role: 'Backend-разработчик',
      city: 'Санкт-Петербург', workFormat: 'HYBRID',
      salaryFrom: 250000, salaryTo: 380000,
      experienceMonthsMin: 36, hoursMin: 40, level: 'SENIOR',
      testMode: 'CUSTOM', matchingStatus: 'READY', generationStatus: 'READY', rankingStatus: 'READY',
      skills: [
        { code: 'SPRING', minLevel: 4, required: true },
        { code: 'JAVA_CORE', minLevel: 4, required: true }
      ]
    }
  ],
  matchesByVacancy: {
    'vac-11': [
      { candidateId: 'cand-1001', score: 88, eligible: true, matched: [{ code: 'B2B_SALES' }, { code: 'NEGOTIATION' }], partial: [{ code: 'CRM' }], missing: [] },
      { candidateId: 'cand-1002', score: 64, eligible: true, matched: [{ code: 'B2B_SALES' }], partial: [{ code: 'CRM' }], missing: [{ code: 'NEGOTIATION' }] },
      { candidateId: 'cand-1003', score: 35, eligible: false, matched: [], partial: [], missing: [{ code: 'B2B_SALES' }, { code: 'NEGOTIATION' }, { code: 'CRM' }] }
    ],
    'vac-12': [
      { candidateId: 'cand-2001', score: 92, eligible: true, matched: [{ code: 'ACCOUNTING' }, { code: '1C' }], partial: [], missing: [] }
    ],
    'vac-13': [],
    'vac-14': [
      { candidateId: 'cand-3001', score: 91, eligible: true, matched: [{ code: 'JAVA_CORE' }, { code: 'SPRING' }], partial: [{ code: 'POSTGRESQL' }], missing: [] },
      { candidateId: 'cand-3002', score: 58, eligible: true, matched: [{ code: 'JAVA_CORE' }], partial: [], missing: [{ code: 'SPRING' }] }
    ],
    'vac-15': [
      { candidateId: 'cand-3001', score: 93, eligible: true, matched: [{ code: 'SPRING' }, { code: 'JAVA_CORE' }], partial: [], missing: [] }
    ]
  },
  rankingByVacancy: {
    'vac-11': {
      final: [
        { rank: 1, candidateId: 'cand-1001', finalScore: 87, state: 'RECOMMENDED' },
        { rank: 2, candidateId: 'cand-1002', finalScore: 72, state: 'RECOMMENDED' }
      ],
      waiting: [
        { candidateId: 'cand-1003', components: { matching: 35 }, state: 'WAITING_TEST' }
      ]
    },
    'vac-12': {
      final: [
        { rank: 1, candidateId: 'cand-2001', finalScore: 94, state: 'RECOMMENDED' }
      ],
      waiting: []
    },
    'vac-13': null,
    'vac-14': {
      final: [
        { rank: 1, candidateId: 'cand-3001', finalScore: 95, state: 'RECOMMENDED' },
        { rank: 2, candidateId: 'cand-3002', finalScore: 61, state: 'RECOMMENDED' }
      ],
      waiting: []
    },
    'vac-15': {
      final: [
        { rank: 1, candidateId: 'cand-3001', finalScore: 96, state: 'RECOMMENDED' }
      ],
      waiting: []
    }
  },
  resultsByVacancy: {
    'vac-11': [
      {
        id: 'asn-r-1',
        candidateId: 'cand-1001', submitted: true, status: 'SCORED',
        result: {
          totalScore: 87, scoringVersion: 'V2',
          breakdown: [
            { competency: 'B2B_SALES', percent: 92, score: 18, maxScore: 20 },
            { competency: 'NEGOTIATION', percent: 80, score: 16, maxScore: 20 },
            { competency: 'CRM', percent: 55, score: 11, maxScore: 20 }
          ]
        }
      },
      { id: 'asn-r-2', candidateId: 'cand-1002', submitted: true, status: 'SCORING_FAILED', result: null },
      { id: 'asn-r-3', candidateId: 'cand-1003', submitted: false, status: 'ASSIGNED', result: null }
    ],
    'vac-12': [
      {
        id: 'asn-r-4',
        candidateId: 'cand-2001', submitted: true, status: 'SCORED',
        result: {
          totalScore: 94, scoringVersion: 'V2',
          breakdown: [
            { competency: 'ACCOUNTING', percent: 96, score: 19, maxScore: 20 },
            { competency: '1C', percent: 90, score: 18, maxScore: 20 }
          ]
        }
      }
    ],
    'vac-13': [],
    'vac-14': [
      {
        id: 'asn-r-5',
        candidateId: 'cand-3001', submitted: true, status: 'SCORED',
        result: {
          totalScore: 95, scoringVersion: 'V2',
          breakdown: [
            { competency: 'JAVA_CORE', percent: 98, score: 20, maxScore: 20 },
            { competency: 'SPRING', percent: 90, score: 18, maxScore: 20 },
            { competency: 'POSTGRESQL', percent: 85, score: 17, maxScore: 20 }
          ]
        }
      },
      { id: 'asn-r-6', candidateId: 'cand-3002', submitted: true, status: 'SCORING_FAILED', result: null }
    ],
    'vac-15': [
      {
        id: 'asn-r-7',
        candidateId: 'cand-3001', submitted: true, status: 'SCORED',
        result: {
          totalScore: 96, scoringVersion: 'V2',
          breakdown: [
            { competency: 'SPRING', percent: 96, score: 19, maxScore: 20 },
            { competency: 'JAVA_CORE', percent: 96, score: 19, maxScore: 20 }
          ]
        }
      }
    ]
  },
  vacancyTestBy: {
    'vac-11': {
      questions: [
        { type: 'SINGLE_CHOICE', text: 'С чего начинается работа с новым клиентом?', options: ['Выяснение потребностей', 'Предложение скидки', 'Презентация всех услуг', 'Закрытие на сделку сейчас'], correctAnswer: { optionIndex: 0 }, maxScore: 10, competency: 'B2B_SALES' },
        { type: 'SINGLE_CHOICE', text: 'Что делает CRM при работе с базой?', options: ['Хранит историю общения с клиентом', 'Считает налоги', 'Планирует поставки', 'Ведёт складской учёт'], correctAnswer: { optionIndex: 0 }, maxScore: 10, competency: 'CRM' },
        { type: 'FREE_TEXT', text: 'Как вы отреагируете на возражение клиента о высокой цене?', rubric: { criteria: ['Аргументированность', 'Работа с возражением'] }, maxScore: 10, competency: 'NEGOTIATION' }
      ]
    },
    'vac-12': {
      questions: [
        { type: 'SINGLE_CHOICE', text: 'Какой документ подтверждает приобретение товара у поставщика?', options: ['Накладная', 'Путевой лист', 'Договор аренды', 'Акт на списание брака'], correctAnswer: { optionIndex: 0 }, maxScore: 10, competency: 'ACCOUNTING' },
        { type: 'SINGLE_CHOICE', text: 'Что отражает оборотно-сальдовая ведомость по счёту?', options: ['Остатки и обороты по счёту', 'Штатное расписание', 'График отпусков', 'Кассовую книгу'], correctAnswer: { optionIndex: 0 }, maxScore: 10, competency: 'ACCOUNTING' },
        { type: 'FREE_TEXT', text: 'Как вы оцените влияние кредиторской задолженности на отчётность компании?', rubric: { criteria: ['Полнота', 'Терминология'] }, maxScore: 10, competency: 'ACCOUNTING' }
      ]
    },
    'vac-14': {
      questions: [
        { type: 'SINGLE_CHOICE', text: 'Что выведет код?\n\n```java\nint[] a = {1, 2, 3};\nSystem.out.println(a[2]);\n```', options: ['3', '1', 'Ошибку ArrayIndexOutOfBounds', 'null'], correctAnswer: { optionIndex: 0 }, maxScore: 10, competency: 'JAVA_CORE' },
        { type: 'SINGLE_CHOICE', text: 'Какой аннотацией Spring мапит GET-запрос?', options: ['@GetMapping', '@PostMapping', '@RequestMapping', '@Bean'], correctAnswer: { optionIndex: 0 }, maxScore: 10, competency: 'SPRING' },
        { type: 'FREE_TEXT', text: 'Почему вы выбрали PostgreSQL для высоконагруженного сервиса?', rubric: { criteria: ['Аргументированность', 'Знание индексов'] }, maxScore: 10, competency: 'POSTGRESQL' }
      ]
    },
    'vac-15': {
      questions: [
        { type: 'SINGLE_CHOICE', text: 'Что управляет бинами в Spring?', options: ['IoC-контейнер', 'JVM GC', 'Maven', 'WebSocket'], correctAnswer: { optionIndex: 0 }, maxScore: 10, competency: 'SPRING' },
        { type: 'SINGLE_CHOICE', text: 'Зачем нужен @Transactional?', options: ['Границы транзакций БД', 'Кэширование', 'Логирование', 'Валидация'], correctAnswer: { optionIndex: 0 }, maxScore: 10, competency: 'SPRING' },
        { type: 'FREE_TEXT', text: 'Как бы вы спроектировали REST API для каталога товаров?', rubric: { criteria: ['Полнота', 'Архитектура'] }, maxScore: 10, competency: 'SPRING' }
      ]
    }
  },
  nextVacancyId: 100,
  nextResultId: 100,
  nextInvId: 100
};

/* ---------------------- Роутер мок-API ---------------------- */

async function demoRequest(path, options) {
  const method = (options.method || 'GET');
  const q = (path.match(/\/api\/v1\/(.+)$/) || [])[1] || '';

  function match(re) { const m = q.match(re); return m ? m[1] : null; }

  // Роль
  if (q === 'me') return { id: 987654321, role: demo.role };
  if (q === 'me/role') { demo.role = options.body.role; return { id: 987654321, role: demo.role }; }

  // Кандидат
  if (q === 'candidate/profile') {
    if (method === 'PUT') { demo.candidate = options.body; return demo.candidate; }
    return demo.candidate;
  }
  if (q === 'candidate/test-assignments') return demo.assignments;
  const asnId = match(/^candidate\/test-assignments\/([^/]+)\/answers$/);
  if (asnId) { const a = demo.assignments.find(x => x.id === asnId); if (a) a.submitted = true; return null; }
  const asnOpen = match(/^candidate\/test-assignments\/([^/]+)$/);
  if (asnOpen) {
    const a = demo.assignments.find(x => x.id === asnOpen);
    return { id: a.id, vacancyId: a.vacancyId, submitted: a.submitted, questions: a.questions || demo.assignmentQuestions };
  }
  if (q === 'candidate/invitations') return demo.invitations;
  const invId = match(/^candidate\/invitations\/([^/]+)\/decision$/);
  if (invId) {
    const inv = demo.invitations.find(x => x.id === invId);
    if (inv && options.body) inv.status = options.body.decision;
    return null;
  }

  // Наниматель
  if (q === 'employer/vacancies') {
    if (method === 'POST') {
      const body = options.body;
      const v = {
        id: 'vac-' + (++demo.nextVacancyId),
        role: body.role, city: body.city || 'Москва',
        workFormat: body.workFormat || 'REMOTE',
        salaryFrom: body.salaryFrom, salaryTo: body.salaryTo,
        experienceMonthsMin: body.experienceMonthsMin, hoursMin: body.hoursMin,
        level: body.level || 'JUNIOR', testMode: body.testMode || 'NONE',
        matchingStatus: 'READY', generationStatus: body.testMode === 'NONE' ? null : 'READY', rankingStatus: 'READY',
        skills: body.skills || []
      };
      demo.vacancies.unshift(v);
      demo.matchesByVacancy[v.id] = [];
      demo.resultsByVacancy[v.id] = [];
      demo.rankingByVacancy[v.id] = { final: [], waiting: [] };
      if (v.testMode !== 'NONE' && body.testMode === 'CUSTOM') {
        demo.vacancyTestBy[v.id] = { questions: (body.customQuestions || []).map((cq, i) => ({
          type: cq.type, text: cq.text, maxScore: cq.maxScore || 10, competency: cq.competency || 'GENERAL',
          options: cq.options, correctAnswer: cq.correctAnswer, rubric: cq.rubric
        })) };
      } else if (v.testMode !== 'NONE') {
        demo.vacancyTestBy[v.id] = { questions: demo.vacancyTestBy['vac-11'].questions };
      }
      return v;
    }
    return demo.vacancies;
  }
  const vacSub = q.match(/^employer\/vacancies\/([^/]+)\/(.+)$/);
  if (vacSub) {
    const idPart = vacSub[1];
    const subPath = vacSub[2];
    const v = demo.vacancies.find(x => x.id === idPart);
    if (subPath === 'matching/recalculate') { if (v) v.matchingStatus = 'READY'; return v; }
    if (subPath === 'test/generate/retry') { if (v) { v.generationStatus = 'READY'; v.rankingStatus = 'READY'; } return v; }
    if (subPath === 'ranking/recalculate') { if (v) v.rankingStatus = 'READY'; return v; }
    if (subPath === 'matches') return demo.matchesByVacancy[idPart] || [];
    if (subPath === 'ranking') return demo.rankingByVacancy[idPart] || null;
    if (subPath === 'results') return demo.resultsByVacancy[idPart] || [];
    if (subPath === 'test') return demo.vacancyTestBy[idPart] || { questions: [] };
    const asnRetry = subPath.match(/^assignments\/([^/]+)\/score\/retry$/);
    if (asnRetry) {
      const a = (demo.resultsByVacancy[idPart] || []).find(x => x.id === asnRetry[1]);
      if (a) { a.status = 'SCORED'; a.submitted = true; a.result = { totalScore: 80, scoringVersion: 'V2', breakdown: [{ competency: 'GENERAL', percent: 80, score: 16, maxScore: 20 }] }; }
      return demo.resultsByVacancy[idPart] || [];
    }
    if (subPath === 'assignments') {
      (demo.resultsByVacancy[idPart] = demo.resultsByVacancy[idPart] || []).push({
        id: 'asn-r-' + (++demo.nextResultId), vacancyId: idPart,
        candidateId: options.body && options.body.candidateId,
        submitted: false, status: 'ASSIGNED', result: null
      });
      return null;
    }
    if (subPath === 'invitations') {
      demo.invitations.push({ id: 'inv-' + (++demo.nextInvId), vacancyId: idPart,
        candidateId: options.body && options.body.candidateId, status: 'PENDING' });
      return null;
    }
    return v || null;
  }
  const vacOpen = match(/^employer\/vacancies\/([^/]+)$/);
  if (vacOpen) return demo.vacancies.find(x => x.id === vacOpen) || null;

  return { ok: false, status: 404, detail: 'Демо: неизвестный маршрут ' + path };
}
