# Notion Import는 새 snapshot을 완성한 뒤 성공 시에만 원자적으로 공개하고 이전 Run Row는 publication에서 제외해 영구 보존한다.

## 상태

Proposed

## 관련 Issue

- #284 [BE] Notion Import 작업자와 원자적 Snapshot 공개 구현

## 한 줄 요약

Notion Import는 새 snapshot을 완성한 뒤 성공 시에만 원자적으로 공개하고 이전 Run Row는 publication에서 제외해 영구 보존한다.

## 왜 이 결정이 필요했나

수동 재가져오기 중 실패하거나 빈 결과가 발생해도 Workspace의 기존 Tree와 AI 검색을 유지해야 한다.

OWNER가 새 Import를 실행하는 중 Notion API 장애, 권한 변화, 빈 결과가 발생하면 기존 Tree와 AI 검색이 빈 데이터로 바뀔 수 있다.

결정 동인:

- 기존 정상 데이터 보호
- 실패 복구
- 과거 출처 재현
- 단순한 full snapshot 구현

## 트레이드 오프

- 기존 Page Row를 수집 중 즉시 갱신한다
- 새 Run snapshot을 staging하고 성공 시 publication pointer를 전환한다

## 무엇을 결정했나

Notion Import는 새 snapshot을 완성한 뒤 성공 시에만 원자적으로 공개하고 이전 Run Row는 publication에서 제외해 영구 보존한다.

외부 API와 DB 작업의 실패 경계를 분리하면서 불완전한 결과가 사용자 조회에 노출되지 않게 한다.

## 결과

- 실패 중에는 이전 성공 데이터가 계속 보인다
- 이전 Run 데이터를 보존해 저장 공간이 증가한다
- publication 전환 transaction을 짧게 유지할 수 있다

## 다시 논의해야 할 조건

- 증분 동기화를 도입할 때
- 이전 Run 보존 비용이 운영 한계를 넘을 때
- checkpoint 재개가 필요해질 때

## 확인

- 예정 경로: `docs/adr/284-notion-import-snapshot-publication.md`
- 결정 주체: Knot 팀
- AI 하네스가 Proposed ADR 파일을 생성했다.
- 팀이 PR에서 승인한 뒤 Accepted로 바꾼다.
