const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const frontend = path.resolve(__dirname, '..');
const html = fs.readFileSync(path.join(frontend, 'index.html'), 'utf8');
const app = fs.readFileSync(path.join(frontend, 'app.js'), 'utf8');
const bridgeAt = html.indexOf('https://st.max.ru/js/max-web-app.js');
const apiAt = html.indexOf('api.js');
assert(bridgeAt !== -1 && bridgeAt < apiAt, 'MAX Bridge must load before api.js');
assert.equal((app.match(/onclick="exitDemo\(\)"/g) || []).length, 2,
  'demo exit must be available on role selection and in role navigation');

const context = vm.createContext({
  window: { WebApp: { initData: 'from-max-bridge' } },
  localStorage: { getItem: () => 'stale-local-data' }
});
vm.runInContext(fs.readFileSync(path.join(frontend, 'api.js'), 'utf8'), context);
assert.equal(vm.runInContext('getInitData()', context), 'from-max-bridge');

context.window.WebApp.initData = '';
assert.equal(vm.runInContext('getInitData()', context), 'stale-local-data');

(async () => {
  const values = new Map();
  const calls = { boot: 0, bootInitData: '', demo: 0, fetch: 0 };
  const authContext = vm.createContext({
    window: { WebApp: { initData: 'fresh-max-init-data' } },
    localStorage: {
      getItem: key => values.get(key) || null,
      setItem: (key, value) => values.set(key, value),
      removeItem: key => values.delete(key)
    },
    FP_CONFIG: { apiBase: 'https://api.example', demoMode: false },
    state: {},
    toast: () => {},
    showRoleSelect: () => {},
    boot: () => {
      calls.boot++;
      calls.bootInitData = vm.runInContext('getInitData()', authContext);
    },
    demoRequest: () => { calls.demo++; return { ok: true }; },
    fetch: async (_url, options) => {
      calls.fetch++;
      calls.authHeader = options.headers['X-Max-Init-Data'];
      return { status: 200, ok: true, json: async () => ({}) };
    }
  });
  vm.runInContext(fs.readFileSync(path.join(frontend, 'api.js'), 'utf8'), authContext);
  vm.runInContext(fs.readFileSync(path.join(frontend, 'demo.js'), 'utf8'), authContext);

  vm.runInContext('enterDemo(); exitDemo();', authContext);
  assert.equal(values.has('fp_demo'), false);
  assert.equal(values.has('fp_initdata'), false);
  assert.equal(calls.boot, 1);
  assert.equal(calls.bootInitData, 'fresh-max-init-data');
  await vm.runInContext("api('/api/v1/me')", authContext);
  assert.equal(calls.demo, 0, 'API must leave demo mode after exitDemo');
  assert.equal(calls.fetch, 1);
  assert.equal(calls.authHeader, 'fresh-max-init-data');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
