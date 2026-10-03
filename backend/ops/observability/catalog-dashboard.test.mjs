import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { buildCatalogDashboard, catalogDashboardPath } from './build-catalog-dashboard.mjs';

const root = new URL('./', import.meta.url);
const dashboard = await buildCatalogDashboard();

test('17175 revision 2 원본과 11개 데이터 패널 배치를 보존한다', async () => {
  const original = await readFile(process.env.KNOT_CATALOG_SOURCE);
  assert.equal(createHash('sha256').update(original).digest('hex'), '339819c22afc205f24e137c1b358059efe8c108f8ca81a0220235327bcdd54d6');
  const source = JSON.parse(original);
  assert.equal(dashboard.uid, 'knot-dev-spring-observability');
  assert.equal(dashboard.timezone, 'Asia/Seoul');
  assert.equal(dashboard.refresh, '30s');
  assert.equal(dashboard.panels.length, 12);
  for (const panel of source.panels) {
    const adapted = dashboard.panels.find(candidate => candidate.id === panel.id);
    assert.equal(adapted.type, panel.type);
    assert.equal(adapted.timeFrom, undefined);
    assert.equal(adapted.timeShift, undefined);
    assert.deepEqual(adapted.gridPos, { ...panel.gridPos, y: panel.gridPos.y + 4 });
  }
});

test('모든 데이터소스와 쿼리가 Dev Spring에 고정되고 Tempo를 요구하지 않는다', () => {
  assert.deepEqual(dashboard.templating.list, []);
  assert.equal(dashboard.__inputs, undefined);
  assert(dashboard.panels[0].options.content.includes('Tempo·OpenTelemetry'));
  assert(dashboard.panels[0].options.content.includes('미구축'));
  for (const panel of dashboard.panels) {
    for (const target of panel.targets ?? []) {
      assert(['knot-prometheus', 'knot-loki'].includes(panel.datasource.uid));
      assert.equal(target.exemplar, false);
      assert(!/application=|compose_service|trace_id|\$app_name|\$log_keyword|DS_/.test(target.expr));
      for (const selector of target.expr.matchAll(/\{([^{}]*)\}/g)) {
        assert(selector[1].includes('env="dev"'));
        assert(selector[1].includes('job="spring"'));
      }
    }
  }
});

test('요청 없는 지연·비율·histogram 결과를 0이나 NaN으로 만들지 않는다', () => {
  for (const panel of dashboard.panels.filter(panel => [6, 8, 18, 20, 23].includes(panel.id))) {
    assert(panel.targets[0].expr.includes('and on(uri)'));
    assert(panel.targets[0].expr.includes('> 0'));
  }
  for (const panel of dashboard.panels.filter(panel => panel.type !== 'text')) {
    assert(!/vector\(0\)|clamp_min/.test(panel.targets[0].expr));
    assert(panel.fieldConfig.defaults.noValue.includes('데이터 없음'));
    assert.notEqual(panel.fieldConfig.defaults.custom?.spanNulls, true);
  }
});

test('생성 파일이 생성 규칙과 같고 기존 Dev 화면으로 시간을 전달한다', async () => {
  assert.deepEqual(JSON.parse(await readFile(new URL(catalogDashboardPath, root), 'utf8')), dashboard);
  for (const link of dashboard.links.filter(link => link.url.startsWith('/d/'))) {
    assert(link.url.startsWith('/d/knot-dev-'));
    assert.equal(link.keepTime, true);
    assert.equal(link.includeVars, false);
  }
});
