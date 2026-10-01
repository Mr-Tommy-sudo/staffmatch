const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const elements = new Map();
function element(id) {
  if (!elements.has(id)) elements.set(id, {
    innerHTML: '', scrollTop: 0, active: false,
    classList: { toggle(_name, active) { this.active = active; } },
    querySelectorAll() { return []; }
  });
  return elements.get(id);
}

const context = vm.createContext({
  isDemoMode: () => false,
  window: { addEventListener() {} },
  document: {
    getElementById: element,
    querySelectorAll() { return []; },
    addEventListener() {}
  },
  console
});
const app = fs.readFileSync(path.join(__dirname, '..', 'app.js'), 'utf8');
vm.runInContext(app, context);
context.toast = () => {};

async function main() {
  vm.runInContext("state.view = 'candidate'", context);
  const calls = [];
  context.api = async path => {
    calls.push(path);
    return path.endsWith('/test-assignments')
      ? [{ id: 'new-test', submitted: false, vacancyId: 'vacancy-1' }]
      : [{ id: 'new-invitation', status: 'PENDING', vacancyId: 'vacancy-2' }];
  };

  vm.runInContext("state.ranking = { final: [], waiting: [{ candidateId: 'candidate-1', components: { matching: 35 } }] }", context);
  const ranking = vm.runInContext('rankingTab()', context);
  assert.match(ranking, /Матчинг: 35/);
  assert.doesNotMatch(ranking, /NaN/);
  vm.runInContext("state.ranking.waiting[0].components.matching = null", context);
  assert.match(vm.runInContext('rankingTab()', context), /Матчинг: —/);

  const loadingTests = context.window.switchTab('tests');
  assert.match(element('cand-tab').innerHTML, /Загрузка/);
  await loadingTests;
  assert.deepEqual(calls, [
    '/api/v1/candidate/test-assignments',
    '/api/v1/candidate/invitations'
  ]);
  assert.match(element('cand-tab').innerHTML, /new-test/);

  await context.window.switchTab('invitations');
  assert.equal(calls.length, 4);
  assert.match(element('cand-tab').innerHTML, /new-invitation/);

  context.api = async path => {
    if (path.endsWith('/test-assignments')) throw Object.assign(new Error('server error'), { status: 500 });
    return [];
  };
  await context.window.switchTab('tests');
  assert.match(element('cand-tab').innerHTML, /Не удалось загрузить тесты/);
  assert.doesNotMatch(element('cand-tab').innerHTML, /new-test/);

  context.api = async () => { throw Object.assign(new Error('unauthorized'), { status: 401 }); };
  await context.window.switchTab('invitations');
  assert.equal(element('view-connect').classList.active, true);
  assert.equal(vm.runInContext('state.invitations.length', context), 0);
}

main().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
