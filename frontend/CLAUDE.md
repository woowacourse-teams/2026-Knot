## 프로젝트 개요

이 프로젝트는 팀 프로젝트에서 문서화의 병목화를 최소화 해주는 앱을 개발하는 레포입니다.

---

## 개발 관련 정보

### 항상 지킬 핵심 규칙

- 모든 컴포넌트는 도메인 로직 포함 여부에 따라 `modules/`(widgets · features) 또는 `shared/components/`(composites · primitives)에 둡니다.
- 추상화 레벨은 `modules` > `composites` > `primitives` 순이며, 자신보다 상위 레벨의 컴포넌트는 임포트할 수 없습니다.
- 모든 컴포넌트는 자신의 폴더를 가지며 `index.tsx`로만 외부에 공개합니다(콜로케이션).
- 동일 레벨 참조는 금지합니다. widgets는 widgets를, features는 features를 임포트할 수 없습니다. 도메인이 겹치면 컴포넌트 재사용 대신 도메인 훅을 `shared/hooks/domain`으로 내려 재사용합니다.

### 작업 전에 반드시 읽을 문서

컴포넌트를 **새로 만들거나, 수정·리팩토링하거나, 코드리뷰할 때**는 작업 전에 아래 문서를 반드시 읽으세요.

- `.claude/rules/component-abstract-pattern.md` — 컴포넌트를 어느 위치(추상화 레벨)에 둘 것인가
- `.claude/rules/component-colocation-pattern.md` — 컴포넌트 폴더를 어떻게 구성할 것인가

컴포넌트 폴더 내부 세그먼트(`ui` / `model` / `utils` / `types` / `constants` / `context`) 코드를 **만들거나 배치·이동할 때**는 아래 문서를 반드시 읽으세요.

- `.claude/rules/segment-pattern.md` — 세그먼트 정의와 배치·의존 규칙

파일·폴더를 **새로 만들거나 위치를 판단할 때**는 아래 문서를 반드시 읽으세요.

- `.claude/rules/project-structure.md` — 전체 폴더 구조와 레이어별 역할, 네이밍·운영 원칙

**테스트 코드를 작성·배치할 때**는 아래 문서를 반드시 읽으세요.

- `.claude/rules/test-strategy.md` — 단위·통합·E2E 테스트의 위치와 대상

**API·훅 코드를 작성할 때**는 아래 문서를 반드시 읽으세요.

- `.claude/rules/api-guide.md` — `shared/api` 구조와 fetch 함수 작성 규칙
- `.claude/rules/query-hooks.md` — 쿼리/뮤테이션 훅 작성 규칙
- `.claude/rules/hook-guide.md` — `shared/hooks`(common/domain) 작성 규칙
