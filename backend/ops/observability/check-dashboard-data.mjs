import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { readFile, readdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';

const run = promisify(execFile);
const root = fileURLToPath(new URL('./', import.meta.url));
const [host, socket] = process.argv.slice(2);
assert(host && socket, 'Usage: node check-dashboard-data.mjs SSH_HOST CONTROL_SOCKET');
const tasks = [];
const layout = [];
const panelsByDashboard = new Map();

const locations = [
  { path: 'dashboards', environments: ['dev', 'prod'] },
  { path: 'provisioning/environment-dashboards/dev', environments: ['dev'] },
  { path: 'provisioning/environment-dashboards/prod', environments: ['prod'] },
];
for (const { path, environments } of locations) {
for (const name of (await readdir(root + path)).filter(name => name.endsWith('.json'))) {
  const dashboard = JSON.parse(await readFile(root + path + '/' + name, 'utf8'));
  assert.equal(new Set(dashboard.panels.map(panel => panel.id)).size, dashboard.panels.length);
  assert(!panelsByDashboard.has(dashboard.uid), `Duplicate dashboard UID: ${dashboard.uid}`);
  panelsByDashboard.set(dashboard.uid, new Set(dashboard.panels.map(panel => String(panel.id))));
  for (const panel of dashboard.panels) {
    assert(panel.gridPos.x >= 0 && panel.gridPos.x + panel.gridPos.w <= 24);
    assert(panel.gridPos.y >= 0 && panel.gridPos.h > 0);
    for (const other of dashboard.panels.filter(other => other.id > panel.id)) {
      const a = panel.gridPos;
      const b = other.gridPos;
      assert(!(a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h), `Overlapping panels: ${dashboard.uid}/${panel.id}/${other.id}`);
    }
    for (const target of panel.targets ?? []) {
      for (const env of environments) {
        tasks.push({dashboard: dashboard.uid, panel: panel.title, env,
          datasource: panel.datasource.uid, logs: panel.type === 'logs',
          expression: target.expr.replaceAll('$env', env).replaceAll('$instance', '.*').replaceAll('$db', '.*').replaceAll('$__range', '30m')});
      }
    }
  }
  layout.push({uid: dashboard.uid, panels: dashboard.panels.length});
}
}

const provision = JSON.parse(await readFile(root + 'provisioning/alerting/knot.json', 'utf8'));
const rules = provision.groups.flatMap(group => group.rules);
assert.equal(new Set(rules.map(rule => rule.uid)).size, rules.length);
for (const rule of rules) {
  const dashboardUid = rule.annotations?.__dashboardUid__;
  const panelId = rule.annotations?.__panelId__;
  assert.equal(Boolean(dashboardUid), Boolean(panelId), `Incomplete alert panel link: ${rule.uid}`);
  if (dashboardUid) {
    assert(panelsByDashboard.get(dashboardUid)?.has(panelId), `Unknown alert panel link: ${rule.uid}`);
  }
  const query = rule.data.find(query => query.refId === 'A');
  tasks.push({alert: rule.uid, datasource: query.datasourceUid, expression: query.model.expr});
}

async function query(task) {
  const endpoint = task.datasource === 'knot-prometheus'
    ? 'http://127.0.0.1:9090/api/v1/query'
    : 'http://127.0.0.1:3100/loki/api/v1/' + (task.logs ? 'query_range' : 'query');
  const quoted = (value) => "'" + value.replaceAll("'", "'\\''") + "'";
  const time = Math.floor(Date.now() / 1000);
  const range = task.logs ? ` --data-urlencode start=${time - 1800} --data-urlencode end=${time} --data-urlencode limit=5` : '';
  const command = 'curl --silent --show-error --max-time 20 --get --data-urlencode '
    + quoted('query=' + task.expression) + range + ' ' + endpoint;
  try {
    const { stdout } = await run('ssh', ['-S', socket, '-o', 'BatchMode=yes', host, command], {timeout: 25000, maxBuffer: 4 * 1024 * 1024});
    const response = JSON.parse(stdout);
    assert.equal(response.status, 'success', response.error);
    const result = response.data.result;
    const values = task.logs ? [] : result.flatMap(series => series.value ? [series.value[1]] : (series.values ?? []).map(value => value[1]));
    return {...task, series: result.length, finite: values.filter(value => Number.isFinite(Number(value))).length,
      nonFinite: values.filter(value => !Number.isFinite(Number(value))).length};
  } catch (error) {
    return {...task, error: error.message};
  }
}

const results = [];
for (let offset = 0; offset < tasks.length; offset += 4) {
  results.push(...await Promise.all(tasks.slice(offset, offset + 4).map(query)));
}
const errors = results.filter(result => result.error);
const empty = results.filter(result => result.series === 0);
const nonFinite = results.filter(result => result.nonFinite > 0);
const brief = ({expression, datasource, logs, ...result}) => result;
console.log(JSON.stringify({layout, rules: rules.length, queries: results.length,
  errors: errors.map(brief), empty: empty.map(brief), nonFinite: nonFinite.map(brief),
  finiteQueries: results.filter(result => result.finite > 0).length}, null, 2));
process.exitCode = errors.length || nonFinite.length ? 1 : 0;
