const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const inputs = new Map();
let levelButtons = [];
const classList = () => ({ toggle() {} });
const screen = {
  querySelectorAll: () => [],
  set innerHTML(html) {
    this.html = html;
    inputs.clear();
    for (const [, tag, id] of html.matchAll(/(<input\b[^>]*\bid="([^"]+)"[^>]*>)/g)) {
      inputs.set(id, { value: tag.match(/\bvalue="([^"]*)"/)?.[1] || '' });
    }
    levelButtons = [...html.matchAll(/<button[^>]*class="([^"]*)"[^>]*data-lvl="([^"]+)"/g)]
      .map(([, classes, level]) => ({ dataset: { lvl: level }, classList: { toggle(_name, active) { this.active = active; } }, active: classes.includes('active') }));
  }
};
const document = {
  getElementById(id) {
    if (id === 'vacancy-screen') return screen;
    if (!inputs.has(id)) inputs.set(id, { value: '' });
    return inputs.get(id);
  },
  querySelectorAll(selector) {
    if (selector === '[data-lvl]') return levelButtons;
    return [];
  }
};
const window = { addEventListener() {} };
const context = vm.createContext({ window, document });
vm.runInContext(fs.readFileSync(`${__dirname}/../app.js`, 'utf8'), context);
context.closeModal = () => {};

vm.runInContext(`
  createForm.level = 'MIDDLE';
  createForm.questions = [
    { type: 'FREE_TEXT', text: 'remove me' },
    { type: 'FREE_TEXT', text: 'keep me' }
  ];
`, context);
context.render('vacancy-screen', context.createVacancyForm());
document.getElementById('vac-role').value = 'Менеджер';
document.getElementById('vac-city').value = 'Москва';
context.setVacLevel('MIDDLE');
context.removeCustomQ(0);
assert.equal(document.getElementById('vac-role').value, 'Менеджер');
assert.equal(document.getElementById('vac-city').value, 'Москва');
assert.match(screen.html, /class="opt active" data-lvl="MIDDLE"/);

context.setTestMode('NONE');
assert.match(screen.html, /class="opt active" data-lvl="MIDDLE"/);
document.getElementById('q-type').value = 'FREE_TEXT';
document.getElementById('q-comp').value = 'NEGOTIATION';
document.getElementById('q-text').value = 'Новый вопрос';
document.getElementById('q-max').value = '10';
document.getElementById('q-criteria').value = 'Критерий';
context.saveQuestion(-1);
assert.match(screen.html, /class="opt active" data-lvl="MIDDLE"/);
assert.equal(document.getElementById('vac-role').value, 'Менеджер');
console.log('vacancy form regressions pass');
