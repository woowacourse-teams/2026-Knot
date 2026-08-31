# Page Tree API는 마지막 성공 Page 전체를 parentPageId 기반 평면 배열로 반환하고 잘못된 계층은 부분 응답 없이 실패한다.

## 상태

Proposed

## 관련 Issue

- #266 [BE] 워크스페이스 Notion Page Tree 조회 API 구현

## 한 줄 요약

Page Tree API는 마지막 성공 Page 전체를 parentPageId 기반 평면 배열로 반환하고 잘못된 계층은 부분 응답 없이 실패한다.

## 왜 이 결정이 필요했나

Page Tree 응답 구조와 실패 정책은 FE 조립 방식, BE 조회 책임과 향후 pagination 또는 lazy loading 변경에 반복 영향을 준다.

Workspace 멤버가 Import 완료 여부와 무관하게 완성된 Page 계층을 탐색해야 하지만 진행 중 데이터, 다른 tenant 데이터 또는 깨진 부분 트리가 노출되면 문서 탐색 결과를 신뢰할 수 없다.

결정 동인:

- 현재 요구를 충족하는 가장 단순한 구현
- FE와 BE의 책임이 분명한 안정적인 API 계약
- 진행 중·실패 Import 데이터와 본문의 비노출
- 실제 문제가 생기기 전 기술과 복잡도를 추가하지 않음

## 트레이드 오프

- 서버가 children을 포함한 중첩 tree를 만들어 반환한다. FE는 단순하지만 서버 조립과 응답 계약이 복잡해진다.
- parentPageId가 있는 평면 배열 전체를 반환한다. FE가 O(n)으로 조립해야 하지만 서버와 계약이 단순하다.
- 최상위와 자식 Page를 단계적으로 lazy loading한다. 큰 tree에는 유리하지만 현재 필요하지 않은 API와 상태 관리가 늘어난다.
- 잘못된 Page를 무시하고 나머지만 반환한다. 가용성은 높지만 사용자가 불완전한 tree를 정상 결과로 오인할 수 있다.

## 무엇을 결정했나

Page Tree API는 마지막 성공 Page 전체를 parentPageId 기반 평면 배열로 반환하고 잘못된 계층은 부분 응답 없이 실패한다.

현재는 전체 Page 수 문제가 관측되지 않았고 parentPageId 평면 배열이 가장 단순하다. 잘못된 계층은 숨기지 않고 실패시켜 불완전한 탐색 결과를 정상으로 보이지 않게 한다.

## 결과

- FE는 parentPageId를 사용해 Page 계층을 조립한다.
- BE는 전체 Page를 한 번에 읽고 계층 유효성을 검사한다.
- 한 Page의 계층 오류가 전체 요청을 실패시킨다.
- 응답 크기 문제가 실제로 관측되기 전에는 pagination, lazy loading과 cache를 도입하지 않는다.

## 다시 논의해야 할 조건

- 실제 Workspace에서 Page Tree 응답 크기, 지연 또는 메모리 문제가 관측될 때
- 잘못된 계층 데이터로 전체 조회 장애가 반복될 때
- FE가 서버 중첩 tree 또는 lazy loading 계약을 요구할 때

## 확인

- 예정 경로: `docs/adr/266-notion-page-tree-read-contract.md`
- 결정 주체: Knot 백엔드 API 담당자
- AI 하네스가 Proposed ADR 파일을 생성했다.
- 팀이 PR에서 승인한 뒤 Accepted로 바꾼다.
