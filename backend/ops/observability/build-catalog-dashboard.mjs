import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';

const root = new URL('./', import.meta.url);
export const catalogDashboardPath = 'provisioning/environment-dashboards/dev/knot-dev-spring-observability.json';

export async function buildCatalogDashboard() {
  assert(process.env.KNOT_CATALOG_SOURCE, 'KNOT_CATALOG_SOURCE에 운영자가 제공한 원본 JSON 경로를 지정하세요.');
  const source = await readFile(process.env.KNOT_CATALOG_SOURCE);
  assert.equal(createHash('sha256').update(source).digest('hex'), '339819c22afc205f24e137c1b358059efe8c108f8ca81a0220235327bcdd54d6', 'Catalog source checksum mismatch');
  const dashboard = JSON.parse(source);
  dashboard.id = null;
  dashboard.uid = 'knot-dev-spring-observability';
  dashboard.title = 'DEV 08 Spring Boot Observability · 17175';
  dashboard.description = 'Grafana catalog 17175 revision 2 (blueswen)에서 Dev 전용으로 조정한 화면입니다. Prometheus·Loki만 연결했으며 Tempo·OTel·exemplar 이동은 미구축입니다.';
  dashboard.tags = ['knot', 'dev', 'spring', 'catalog-17175'];
  dashboard.version = 1;
  dashboard.time = { from: 'now-30m', to: 'now' };
  dashboard.timezone = 'Asia/Seoul';
  dashboard.refresh = '30s';
  dashboard.templating = { list: [] };
  dashboard.editable = false;
  dashboard.links = [
    { title: '01 전체 상태', type: 'link', url: '/d/knot-dev-overview', keepTime: true, includeVars: false },
    { title: '03 로그', type: 'link', url: '/d/knot-dev-logs', keepTime: true, includeVars: false },
    { title: '05 Spring 앱', type: 'link', url: '/d/knot-dev-spring', keepTime: true, includeVars: false },
    { title: '06 Tomcat·DB 연결 풀', type: 'link', url: '/d/knot-dev-pools', keepTime: true, includeVars: false },
    { title: '원본 17175 · revision 2', type: 'link', url: 'https://grafana.com/grafana/dashboards/17175-spring-boot-observability/', targetBlank: true },
  ];
  delete dashboard.__inputs;
  const selectors = 'env="dev",job="spring"';
  const countByUri = 'sum by(uri) (http_server_requests_seconds_count{' + selectors + ',uri!="/actuator/prometheus"})';
  const rateByUri = 'sum by(uri) (rate(http_server_requests_seconds_count{' + selectors + ',uri!="/actuator/prometheus"}[2m]))';
  const titles = new Map([
    [4, ['앱 시작 이후 전체 HTTP 요청', '앱 시작 이후 누적 요청입니다. health를 포함하고 지표 수집 요청은 제외합니다. 선택 기간의 합계나 Nginx 업무 API 요청량과 다릅니다.']],
    [16, ['앱 시작 이후 URI별 요청', 'URI별 누적 HTTP 요청입니다. 앱 재시작 시 초기화되며 health 요청을 포함합니다.']],
    [6, ['URI별 평균 응답 시간 · 최근 2분', '최근 2분 요청이 실제로 있었던 URI만 표시합니다. 요청이 없으면 평균을 0으로 만들지 않습니다.']],
    [22, ['앱 시작 이후 서버 오류 요청', 'outcome=SERVER_ERROR로 기록된 요청의 누적 건수입니다. 해당 시계열이 없으면 0이나 정상으로 바꾸지 않습니다.']],
    [18, ['URI별 누적 2xx 비율', '앱 시작 이후 HTTP 상태 비율입니다. 요청이 없는 URI는 비워 두며 최근 5분 Nginx 비율과 구분합니다.']],
    [20, ['URI별 누적 5xx 비율', '앱 시작 이후 HTTP 상태 비율입니다. 5xx 시계열이 없을 때 데이터를 임의로 만들지 않습니다.']],
    [8, ['URI별 응답 시간 p99 · 최근 2분', '실제 Micrometer histogram bucket으로 계산합니다. 요청이 없는 URI와 NaN은 표시하지 않습니다. exemplar·Tempo 이동은 연결하지 않았습니다.']],
    [23, ['URI별 응답 시간 p95 · 최근 2분', '실제 Micrometer histogram bucket으로 계산합니다. 요청이 없는 URI와 NaN은 표시하지 않습니다.']],
    [12, ['URI별 초당 요청 · 최근 2분', 'Spring 내부 HTTP 요청률이며 health를 포함합니다. Nginx 업무 API 요청량과 기준이 다릅니다.']],
    [14, ['Spring 로그 수준별 발생률', '기존 journald Spring 로그에서 TRACE/DEBUG/INFO/WARN/ERROR를 추출합니다. 원본의 OTel 로그 패턴과 compose_service 라벨을 사용하지 않습니다.']],
    [2, ['DEV Spring 앱 로그 원문', '현재 수집 중인 Dev Spring 로그입니다. 기존 로그 원문을 보존하며 trace ID가 있다고 가정하지 않습니다. 로그가 없으면 조회 기간과 수집 상태를 확인하세요.']],
  ]);
  for (const panel of dashboard.panels) {
    delete panel.timeFrom;
    delete panel.timeShift;
    delete panel.hideTimeOverride;
    panel.gridPos.y += 4;
    [panel.title, panel.description] = titles.get(panel.id);
    panel.fieldConfig ??= { defaults: {}, overrides: [] };
    panel.fieldConfig.defaults ??= {};
    panel.fieldConfig.defaults.noValue = '데이터 없음 / 요청·수집 확인';
    if (panel.fieldConfig.defaults.custom) panel.fieldConfig.defaults.custom.spanNulls = false;
    const datasource = panel.type === 'logs' || panel.id === 14
      ? { type: 'loki', uid: 'knot-loki' }
      : { type: 'prometheus', uid: 'knot-prometheus' };
    panel.datasource = datasource;
    for (const target of panel.targets) {
      target.datasource = datasource;
      target.expr = target.expr.replaceAll('application="$app_name"', selectors);
      target.exemplar = false;
      if ([4, 16, 22, 6].includes(panel.id)) {
        target.instant = true;
        target.range = false;
      }
      if (panel.id === 6) target.expr = '(sum by(uri)(rate(http_server_requests_seconds_sum{' + selectors + ',uri!="/actuator/prometheus"}[2m])) / ' + rateByUri + ') and on(uri) (' + rateByUri + ' > 0)';
      if ([18, 20].includes(panel.id)) target.expr = '(' + target.expr + ') and on(uri) (' + countByUri + ' > 0)';
      if ([8, 23].includes(panel.id)) target.expr = '(' + target.expr.replace('[1m]', '[2m]') + ') and on(uri) (' + rateByUri + ' > 0)';
      if (panel.id === 12) target.expr = rateByUri;
      if (panel.id === 14) {
        target.expr = 'sum by(level) (rate({project="knot",env="dev",job="spring"} | regexp "(?P<level>TRACE|DEBUG|INFO|WARN|ERROR)" | level != "" [2m]))';
        target.legendFormat = '{{level}}';
      }
      if (panel.id === 2) target.expr = '{project="knot",env="dev",job="spring"}';
    }
    if (panel.type === 'logs') Object.assign(panel.options, { wrapLogMessage: true, showTime: true, showLabels: true });
  }
  dashboard.panels.unshift({
    id: 1, type: 'text', title: 'DEV · 원본 구성과 현재 지원 범위',
    gridPos: { x: 0, y: 0, w: 24, h: 4 }, options: { mode: 'markdown', content: '**17175 revision 2 · blueswen**의 요청·응답 분포·로그 배치를 Dev에 추가했습니다. **env=dev / job=spring 고정**, 기존 Prometheus·Loki를 사용합니다.\n\n누적 요청은 앱 시작 이후 값이며 health를 포함합니다. 지연은 최근 2분 실제 요청에만 표시합니다. **Tempo·OpenTelemetry·exemplar/trace ID 이동은 미구축**입니다. 데이터 없음은 정상 0이 아닙니다. 기존 05·06 화면은 그대로 유지합니다.' },
  });
  return dashboard;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await writeFile(new URL(catalogDashboardPath, root), JSON.stringify(await buildCatalogDashboard(), null, 2) + '\n');
}
