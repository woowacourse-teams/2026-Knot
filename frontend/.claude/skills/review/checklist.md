# 코드 리뷰 체크리스트

`/review` 스킬과 `code-reviewer` 에이전트가 **공통으로 참조하는 단일 리뷰 기준**.
리뷰 기준을 여기 한 곳에만 두어, 두 도구의 판정이 갈리지 않도록 함.

- `.claude/rules/*.md`가 바뀌면 **이 파일도 함께 갱신**.
- 지적할 때는 항목 번호(`C-1`)와 근거 문서를 함께 인용.
- 이 목록에 없는 사항은 **취향 차이로 보고 지적하지 않음.** 지적해야 한다면 `제안`으로 두고 취향임을 명시.

## 심각도

| 심각도 | 기준                                                    |
| ------ | ------------------------------------------------------- |
| `치명` | 버그·데이터 손상·보안 문제. 머지 전 반드시 수정.        |
| `주요` | 프로젝트 규칙 위반, 명백한 설계 문제. 수정 권장.        |
| `제안` | 가독성·일관성 개선. 반영 여부는 작성자 판단.            |
| `질문` | 의도가 파악되지 않아 확인이 필요한 부분. 단정하지 않음. |

## 판정

- **승인** : `치명`·`주요` 없음
- **주의** : `주요`만 존재 (합의 후 머지 가능)
- **차단** : `치명` 1건 이상

---

## A. 보안 — 기본 `치명`

- [ ] A-1. 하드코딩된 자격 증명 (API 키, 비밀번호, 토큰, 시크릿)
- [ ] A-2. 토큰을 `localStorage`에 저장하는 등 탈취에 취약한 저장 방식
- [ ] A-3. XSS — `dangerouslySetInnerHTML`, 이스케이프되지 않은 사용자 입력
- [ ] A-4. 사용자 입력이 URL·경로·쿼리로 검증 없이 흘러들어감 (경로 탐색, 오픈 리다이렉트)
- [ ] A-5. 인증·인가 우회 — 라우트 가드 누락, 클라이언트 검증만으로 권한 판단
- [ ] A-6. 민감 정보가 `console`·에러 메시지·URL 쿼리로 노출
- [ ] A-7. 입력 검증 누락 (폼 값, URL 파라미터, API 응답)

## B. 정확성·버그 — `치명` 또는 `주요`

- [ ] B-1. 잘못된 로직·조건 (부정 조건 오류, off-by-one, 잘못된 비교 연산자)
- [ ] B-2. 경계값 미처리 — 빈 배열, `null`/`undefined`, 0, 빈 문자열
- [ ] B-3. 에러를 삼킴 — 빈 `catch`, `.catch(() => {})`, 실패를 성공으로 처리
- [ ] B-4. 비동기 경쟁 상태 — 언마운트 후 setState, 순서 보장 없는 연속 요청, 중복 제출 방지 누락
- [ ] B-5. 옵셔널 체이닝으로 에러를 가리기만 함 (`a?.b?.c`로 실제 문제를 은폐)
- [ ] B-6. 타입 단언(`as`)으로 실제와 다른 타입을 강제

---

## C. 프로젝트 구조 규칙 — 기본 `주요`

각 항목은 `.claude/rules/`에 근거가 있음. 위반 시 **문서 경로와 규칙 문장을 인용**.

### C-1. 추상화 레벨·참조 방향

> 근거: `.claude/rules/component-abstract-pattern.md`

- [ ] C-1-1. 컴포넌트가 도메인 로직 포함 여부에 맞는 레이어에 있는가
      (도메인 O → `modules/`, 도메인 X → `shared/components/`)
- [ ] C-1-2. 참조 방향이 `pages > widgets > features > composites > primitives`를 지키는가.
      **상위 레벨 임포트 금지.** (features가 widgets를 임포트 ❌)
- [ ] C-1-3. **동일 레벨 조합 금지** — widgets↔widgets, features↔features, composites↔composites.
      `primitives`끼리만 조합 가능하며, 그 결과도 `primitives`.
- [ ] C-1-4. `pages`가 도메인 로직·데이터 패칭을 갖고 있지 않은가. (배치·조립만 담당)
- [ ] C-1-5. widgets/features 판단이 맞는가 — 섹션 단위(H2 보유)면 widgets, 재사용되는 작은 단위면 features
- [ ] C-1-6. widgets·features가 props를 과하게 받고 있지 않은가. (직접 패칭하는 독립 단위이므로 `index.tsx` 기준 props가 거의 없어야 함)
- [ ] C-1-7. 같은 데이터를 widgets→features로 props drilling 하고 있지 않은가.
      (동일 `queryKey`로 캐시를 공유해야 함)
- [ ] C-1-8. 도메인이 겹친다는 이유로 컴포넌트를 재사용하고 있지 않은가.
      (컴포넌트 재사용 대신 `shared/hooks/domain`으로 훅을 내려 재사용)
- [ ] C-1-9. `ui/` 서브 컴포넌트가 부모의 추상화 레벨 규칙을 어기지 않는가
      (primitives의 서브 컴포넌트가 composites를 임포트 ❌)
- [ ] C-1-10. **규칙에 없는 불필요한 추상화를 만들지 않았는가**

### C-2. 콜로케이션

> 근거: `.claude/rules/component-colocation-pattern.md`

- [ ] C-2-1. 모든 컴포넌트가 자기 폴더 + `index.tsx`를 갖는가.
      `modules/*`, `shared/components/*` 아래 컴포넌트 파일이 직접 놓이지 않았는가
- [ ] C-2-2. 외부에 공개하는 것이 `index.tsx`뿐인가. 세그먼트 내부 파일이 외부에서 임포트되고 있지 않은가
- [ ] C-2-3. 컴포넌트 관련 파일이 전부 해당 폴더 세그먼트 안에 있는가 (예외: 통합 테스트 `test.tsx`)
- [ ] C-2-4. 컨텍스트로 강결합된 하위 컴포넌트가 상위의 `ui/`에 코로케이션되어 있는가

### C-3. 세그먼트

> 근거: `.claude/rules/segment-pattern.md`

- [ ] C-3-1. 세그먼트 이름이 `ui`/`model`/`utils`/`types`/`constants`/`context` 중 하나인가
- [ ] C-3-2. **세그먼트 내부는 플랫 파일** — `utils/formatDate/index.ts` ❌ → `utils/formatDate.ts` ✅
- [ ] C-3-3. 컴포넌트 폴더에 **`api` 세그먼트 없음** (API 코드는 `shared/api`)
- [ ] C-3-4. 컴포넌트 폴더에 **`hooks` 폴더 없음** (강결합 훅은 `model`)
- [ ] C-3-5. 타입은 `types/` 폴더 안 내용명 파일 — `types.ts` 단일 파일 ❌, `types/index.ts` ❌
- [ ] C-3-6. `context` 세그먼트에 `createContext`·`useContext`·`Provider`가 **한 파일에** 있는가. `.tsx` 확장자인가
- [ ] C-3-7. 특정 컴포넌트 전용 컨텍스트를 `shared/provider`에 두지 않았는가
      (전역 컨텍스트·QueryClient·ThemeProvider만 `shared/provider`)

### C-4. 훅 위치 — 4대 판단 기준

> 근거: `.claude/skills/project-structure/SKILL.md` 「코드를 shared로 내리는 기준」, `.claude/rules/hook-guide.md`

**"지금은 여기서만 쓴다"는 판단 근거가 아님.** 강결합 여부로만 판단.

- [ ] C-4-1. **이름** — 이름에서 컴포넌트명을 지웠을 때 의미가 남는가
      `useCourseDetailBottomSheetState` → `model` / `useGetCoursePath` → `shared/hooks/domain`
- [ ] C-4-2. **인자** — `ref`·`setState`·로컬 상태를 받으면 `model`, 도메인 값만 받으면 `shared`
- [ ] C-4-3. **반환값** — `headerProps`처럼 특정 JSX 전제 묶음이면 `model`, 도메인 개념이면 `shared`
- [ ] C-4-4. **이식 테스트** — 다른 화면에 붙여도 말이 되면 `shared`, Context 밖에서 무의미하면 `model`
- [ ] C-4-5. 도메인과 무관한 UI 로직인데 `modules/**/model`에 있지 않은가
      (→ `shared/components/composites/{Component}/model`)
- [ ] C-4-6. `shared/hooks` 훅이 도메인 여부로 `domain`/`common`에 올바르게 분류됐는가.
      domain 훅은 도메인별 디렉토리로 묶였는가 (`hooks/domain/course/useGetCoursePath/index.ts`)
- [ ] C-4-7. `shared/hooks`의 훅이 훅 이름 디렉토리 + `index.ts` 형태인가
- [ ] C-4-8. 쿼리·뮤테이션 훅이 `shared/hooks`가 아니라 `shared/api`에 있는가

### C-5. API

> 근거: `.claude/rules/api-guide.md`

- [ ] C-5-1. `fetch/` 하위 폴더 경로가 실제 엔드포인트 경로와 일치하는가
      (`GET /api/v1/users/[id]` → `fetch/api/v1/users/[id]/index.ts`)
- [ ] C-5-2. 경로 상수가 `FACILITY_API_PATH`처럼 SNAKE_CASE인가
- [ ] C-5-3. 요청·응답 타입을 `fetch/` 파일 안에 정의하지 않고 `dto/`에서 가져오는가.
      **값 import는 `fetch/`(응답 클래스)·`mutations/`(요청 클래스)만 허용**, 그 외 `Raw`·`Input`·인자용 요청 클래스는 `import type`.
      `queries/`·`suspense/`·`prefetch/`·`mock/`이 `dto/`를 import하고 있으면 ❌
- [ ] C-5-4. 요청 함수가 `getFacilitiesApi` 형식인가 (목적 + 메서드 + `Api`)
- [ ] C-5-5. `async` 함수이며 **`new XxxResponseDto(response.data)`만 반환**하는가
      (`response` 통째 반환 ❌, `response.data` 그대로 반환 ❌). `httpClient` 제네릭에는 클래스가 아니라 `XxxResponseRaw`를 넣었는가
- [ ] C-5-6. **JSDoc 필수** — `@description`·`@param`·`@returns`·`@example`이 모두 있는가
- [ ] C-5-7. `httpClient` 인스턴스를 사용하는가. 컴포넌트에서 직접 `fetch`/`axios` 호출하지 않는가
- [ ] C-5-8. 인증 토큰 주입·공통 에러 정규화가 인터셉터가 아닌 개별 함수에 흩어져 있지 않은가

> DTO — 근거: `.claude/rules/dto-guide.md`

- [ ] C-5-9. DTO가 `src/shared/api/dto/{도메인}.ts`에 **`class`**로 있는가 (`interface` DTO ❌, 엔드포인트별 폴더 · `dto/index.ts` · `shared/types` ❌)
- [ ] C-5-10. 같은 도메인의 DTO가 한 파일에 모여 있는가. 새 파일은 새 도메인일 때만, 파일명은 `queryKey/`·`mock/types/`와 동일
- [ ] C-5-11. 이름이 클래스 `{Method}{Resource}RequestDto` / `{Method}{Resource}ResponseDto` / `{Name}Dto`, 생성자 입력 `…RequestInput` / `…ResponseRaw` / `{Name}Raw`인가. 요청 함수 이름과 대응하는가
- [ ] C-5-12. **주석** — 파일 상단에 엔드포인트 목록, 클래스마다 한 줄 설명, **모든 클래스 필드**에 의미·nullable 조건·형식·제약·예시 중 해당 항목이 있는가.
      생성자에서 변환한 필드는 변환 내용(`서버 null → 빈 문자열`)을 적었는가. `Raw`·`Input` 필드 주석은 클래스와 중복이면 생략 가능.
      이름을 되풀이한 주석 ❌, `@property`로 클래스 위에 몰아 쓰기 ❌
- [ ] C-5-13. `dto/`에 **DTO 클래스와 생성자 입력 `interface`(`Raw`·`Input`)만** 있는가 (상수 · 함수 · enum ❌)
- [ ] C-5-14. `dto/`가 다른 폴더를 import하지 않고, `mock/`과 `shared/api` 밖에서 `dto/`를 import하지 않는가
- [ ] C-5-15. **생성자가 변환만 하는가** — 필드 선별·기본값·`null` 정규화·이름 변경·형식 변환·중첩 조각 `new`만 허용.
      `throw`·형식 검사 같은 **검증 로직 ❌**. 변환 없는 필드도 `this.id = raw.id`로 그대로 대입했는가
- [ ] C-5-16. 클래스에 **메서드·getter·`#private` 필드가 없는가** — 필드만. 파생 값은 사용하는 쪽이나 `shared/utils`에서 계산
- [ ] C-5-17. **`new XxxDto(...)`의 위치** — 응답 클래스는 `fetch/` 요청 함수, 요청 클래스는 `mutations/`의 `mutationFn`에서만.
      요청 함수 안에서 요청 클래스를 `new` ❌, 컴포넌트·`shared/hooks`·`mock/`에서 `new` ❌ (`grep -rn "new [A-Za-z]*Dto(" src`로 확인)
- [ ] C-5-18. **`Raw`/`Input`이 클래스와 분리돼 있는가** — 응답 클래스의 생성자 입력은 서버 JSON 모양(`Raw`), 요청 클래스의 생성자 입력은 앱 값(`Input`).
      클래스를 `httpClient` 제네릭이나 `mutationFn` 인자 타입에 그대로 쓰지 않았는가

### C-6. 쿼리·뮤테이션 훅

> 근거: `.claude/rules/query-hooks.md`

- [ ] C-6-1. 위치가 맞는가 — `queries/` / `mutations/` / `suspense/` / `prefetch/`
- [ ] C-6-2. 이름이 맞는가 — `useTodosQuery`, `useTodosSuspenseQuery`, `useTodosPrefetchQuery`, `useUpdateTodoMutation`
- [ ] C-6-3. 훅 이름 디렉토리 + `index.ts` 형태인가
- [ ] C-6-4. `queryKey`를 훅 안에서 직접 만들지 않고 `queryKey/`의 factory에서 가져오는가
- [ ] C-6-5. **같은 리소스의 query·suspense·prefetch가 동일한 queryKey factory + 동일한 fetch 함수를 쓰는가**
      (어긋나면 prefetch 캐시를 읽지 못함)
- [ ] C-6-6. 인자가 `함수명 + Params` 인터페이스인가
- [ ] C-6-7. 얇은 래핑인가 — 반환값 있는 훅은 그대로 `return`, `usePrefetchQuery`는 호출만
- [ ] C-6-8. 뮤테이션 성공 후 관련 쿼리 무효화가 누락되지 않았는가
- [ ] C-6-9. 뮤테이션 훅의 `mutationFn`이 `{Method}{Resource}RequestInput`을 받아 **`new XxxRequestDto(input)`**을 만들어 요청 함수에 넘기는가.
      컴포넌트가 `dto/`를 import하거나 `new`를 부르고 있으면 ❌ (`.claude/rules/dto-guide.md`)
- [ ] C-6-10. 쿼리 훅의 `data`(응답 DTO 인스턴스)를 `useEffect`·`useMemo` deps에 **참조 그대로** 넣지 않았는가.
      클래스 인스턴스는 structural sharing 대상이 아니라 refetch마다 참조가 바뀜 → `data.id` 같은 원시값을 deps에

### C-7. 테스트 위치

> 근거: `.claude/rules/test-strategy.md`

- [ ] C-7-1. 단위 테스트가 구현 파일 옆에 있는가
      (세그먼트: `utils/formatDate.ts` + `utils/formatDate.test.ts` / `shared/utils`: `formatDate/index.ts` + `formatDate/test.ts`)
- [ ] C-7-2. 통합 테스트가 최종 책임 컴포넌트(주로 widgets) 폴더의 `test.tsx`인가
- [ ] C-7-3. E2E가 전역 `src/__test__/`에 있는가 (페이지 폴더에 두지 않음)
- [ ] C-7-4. **훅 테스트를 작성하지 않았는가** — 훅은 테스트 대상이 아님. 작성돼 있으면 지적
- [ ] C-7-5. `*.stories.tsx` 등 스토리북 파일을 새로 만들지 않았는가 (보류 상태)

---

## D. 네이밍·타입·작성 규칙 — 기본 `주요`, 사소하면 `제안`

> 근거: `.claude/rules/general-code-convention.md`

### D-1. 네이밍

- [ ] D-1-1. 폴더 `camelCase`, 컴포넌트 파일 `PascalCase.tsx`, 그 외 `camelCase.ts`
      (예외: `src/pages` 하위 라우트 폴더는 kebab-case 허용)
- [ ] D-1-2. Boolean은 `is` 접두 (`isOpen`, `isLoading`)
- [ ] D-1-3. 복수 요소는 `s` 접미 (`todos`), 리스트 UI는 `List` 접미 (`TodoList`)
- [ ] D-1-4. **props로 받는 핸들러는 `on*`, 컴포넌트 내부 핸들러는 `handle*`**
- [ ] D-1-5. 레이아웃 요소 — 2개 이상 감싸면 `Container`, 1개면 `Wrapper`
- [ ] D-1-6. 상수 `SNAKE_CASE`, **함수 내부 상수는 `camelCase`**
- [ ] D-1-7. 컨텍스트는 `이름 + Context` (`store` 네이밍 사용 안 함)
- [ ] D-1-8. 의미 없는 이름(`x`, `tmp`, `data`, `item2`)을 쓰지 않았는가
- [ ] D-1-9. 설명 없는 매직 넘버·매직 스트링이 없는가

### D-2. 타입

- [ ] D-2-1. **`any` 금지**
- [ ] D-2-2. 객체는 `interface`, 나머지는 `type` (예외: `shared/api/dto/`의 DTO는 `class`. 그 밖의 `class` 사용은 지적)
- [ ] D-2-3. **리턴 타입을 명시하지 않았는가** — 추론에 맡기는 것이 규칙. 명시했다면 지적
- [ ] D-2-4. 컴포넌트 props 타입이 `컴포넌트명 + Props` **인터페이스**인가
- [ ] D-2-5. 훅·함수 인자 타입이 `함수명 + Params` **인터페이스**인가
- [ ] D-2-6. API DTO 클래스에 `RequestDto`/`ResponseDto`, 생성자 입력에 `RequestInput`/`ResponseRaw` 접미가 붙었는가
- [ ] D-2-7. 불필요한 단언(`as`)·`!` 논넌널 단언을 쓰지 않았는가

### D-3. 컴포넌트·훅·함수

- [ ] D-3-1. 컴포넌트가 `export default function` 형식인가
- [ ] D-3-2. 커스텀 훅이 `use*`로 시작하고 **객체를 반환**하는가 (배열 반환 ❌)
- [ ] D-3-3. 파라미터가 2개 이상일 때 객체 구조 분해로 받는가

### D-4. 임포트 경로

- [ ] D-4-1. 경로 별칭(`@/*`)을 쓰는가. 상위 디렉토리를 타고 올라가는 상대경로(`../../`)가 없는가
- [ ] D-4-2. 상대경로가 **같은 디렉토리 또는 하위 디렉토리**에만 쓰였는가
- [ ] D-4-3. 절대경로 그룹과 상대경로 그룹이 빈 줄로 구분됐는가

### D-5. 스타일

- [ ] D-5-1. **Emotion(`@emotion/styled`, `@emotion/react`)으로 작성**했는가
- [ ] D-5-2. 색상·간격·타이포 값을 하드코딩하지 않고 ThemeProvider의 디자인 토큰을 쓰는가
- [ ] D-5-3. 스타일 컴포넌트가 렌더 함수 내부에서 정의되지 않았는가 (매 렌더 재생성)

---

## E. React — `주요` 또는 `제안`

- [ ] E-1. 훅 규칙 위반 — 조건문·반복문·early return 이후 훅 호출
- [ ] E-2. `useEffect` 의존성 배열 누락·과다, 불필요한 의존성으로 인한 무한 루프
- [ ] E-3. **파생 상태를 `useState` + `useEffect`로 동기화** — 렌더 중 계산으로 대체 가능한가
- [ ] E-4. 없어도 되는 `useEffect` (이벤트 핸들러에서 처리 가능한 로직)
- [ ] E-5. 리스트 `key`에 인덱스 사용 (순서가 바뀔 수 있는 목록)
- [ ] E-6. 상태를 직접 변형 — 불변성 위반 (`push`, `sort`, 객체 속성 직접 할당)
- [ ] E-7. 상태 끌어올리기로 해결한 UI 로직을 composites의 컴파운드/FACC 패턴으로 내릴 수 있는가
- [ ] E-8. 렌더마다 새로 만들어지는 객체·함수가 자식의 리렌더를 유발하는가
- [ ] E-9. 로딩·에러 상태 처리가 누락되지 않았는가 (Suspense/ErrorBoundary 또는 명시적 분기)

## F. 접근성 — `주요`

- [ ] F-1. 시맨틱 태그 사용 — 클릭 가능한 요소가 `div`/`span`이 아닌가
- [ ] F-2. `label`이 입력 요소와 연결됐는가 (`htmlFor`)
- [ ] F-3. 키보드로 조작 가능한가 (포커스 이동, Enter/Space, Esc로 닫기)
- [ ] F-4. 아이콘 전용 버튼에 접근 가능한 이름이 있는가 (`aria-label`)
- [ ] F-5. 이미지에 의미 있는 `alt`가 있는가 (장식용이면 `alt=""`)
- [ ] F-6. 상태를 색상만으로 전달하지 않는가 (에러를 빨간색으로만 표시)
- [ ] F-7. 모달·바텀시트에 포커스 트랩과 `role`/`aria-modal`이 있는가

## G. 성능 — `제안`, 명백한 병목이면 `주요`

- [ ] G-1. 불필요하게 O(n²)인 로직 (반복문 안의 `find`/`includes`)
- [ ] G-2. 렌더마다 반복되는 비싼 연산에 메모이제이션이 없는가
- [ ] G-3. 큰 라이브러리를 전체 임포트하지 않는가 (번들 크기)
- [ ] G-4. 라우트 단위 코드 스플리팅이 필요한 규모인가
- [ ] G-5. 이미지 크기·포맷이 최적화됐는가

## H. 코드 품질 정량 — `제안`

- [ ] H-1. 함수 50줄 초과
- [ ] H-2. 파일 800줄 초과
- [ ] H-3. 중첩 4레벨 초과
- [ ] H-4. **`console.log` 잔존**
- [ ] H-5. **티켓 번호 없는 `TODO`/`FIXME`**
- [ ] H-6. 중복 코드 — 같은 로직이 3회 이상 반복
- [ ] H-7. 주석 처리된 죽은 코드
- [ ] H-8. **코드·주석에 이모지 사용**
- [ ] H-9. 새로 추가된 라이브러리의 라이선스·번들 영향을 확인했는가

---

## 알려진 예외 — 지적하지 않음

아래는 이미 팀이 인지한 상태이므로 **개별 항목으로 지적하지 않음.** 필요하면 리뷰 문서 끝의 `참고` 섹션에 **1회만** 언급.

| 사항                                                                  | 처리                                                                                                       |
| --------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| 테스트 러너 미도입 (vitest·jest·testing-library·playwright 모두 없음) | 파일마다 "테스트 없음"을 반복 지적하지 않음. 테스트가 **필요한 로직**이 추가된 경우에만 문서 끝에 1회 정리 |
| `src/pages` 하위 kebab-case 라우트 폴더 (`join-error` 등)             | 규칙상 허용. 지적하지 않음                                                                                 |
| 라우팅 규약용 특수 폴더 (`_layout`, `[workspaceId]`)                  | 규약 표기. 지적하지 않음                                                                                   |
| Tailwind 미사용                                                       | 이 레포는 Emotion을 사용. Tailwind 부재를 위반으로 보지 않음                                               |
| `src/modules` 폴더 부재                                               | 아직 도메인 컴포넌트가 없어서일 뿐. 폴더 부재 자체를 지적하지 않음                                         |

> 위 예외가 해소되면(테스트 러너 도입 등) 이 표에서 항목을 제거할 것.
