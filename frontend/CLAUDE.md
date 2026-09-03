## 프로젝트 개요

이 프로젝트는 팀 프로젝트에서 문서화의 병목화를 최소화 해주는 앱을 개발하는 레포입니다.

---

### 작업 절차

코드 구현 시 아래 순서를 따라야 한다.

1. **대상 파일을 먼저 특정한다.** 요구사항을 보고 어떤 파일을 새로 만들거나 고쳐야 하는지 정한다. 위치·역할 판단의 근거는 `project-structure` 스킬이다.
2. **대상 파일과 그 주변을 읽는다.** 고칠 파일, 같은 폴더의 기존 파일, 참고할 유사 구현을 먼저 읽는다. 새 파일이라면 들어갈 폴더의 기존 파일을 읽는다.
3. **주입된 규칙을 확인한 뒤 구현한다.** 규칙이 주입되지 않았다면 아직 대상 영역의 파일을 읽지 않은 것이므로 2단계로 돌아간다.

영역별로 주입되는 규칙:

- `src/modules/**`, `src/shared/components/**` → `component-colocation-pattern.md`, `segment-pattern.md`
- `src/shared/**` → `shared-layer.md`
- `src/shared/hooks/**` → `hook-guide.md`
- `src/shared/api/**` → `api-guide.md`, `query-hooks.md`
- `src/**/test.ts(x)`, `src/**/*.test.ts`, `src/__test__/**` → `test-strategy.md`
- `src/**/*.ts(x)` 전체 → `general-code-convention.md`
