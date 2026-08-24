---
paths:
  - "src/modules/**"
  - "src/shared/components/**"
description: react 컴포넌트가 지켜야 하는 추상화 레벨을 정의한 가이드라인. 컴포넌트 생성·수정·리팩토링·코드리뷰 전 필독.
---

# 리액트 컴포넌트 추상화 가이드라인

이 문서는 해당 프로젝트의 모든 컴포넌트가 지켜야 하는 컴포넌트 추상화 레벨을 정의함.

이 문서는 **컴포넌트를 어느 위치에 둘 것인가**만 다룸. 위치가 정해진 이후는 아래 문서 참고.

- `.claude/rules/component-colocation-pattern.md` : 컴포넌트 폴더를 어떻게 구성할 것인가
- `.claude/rules/segment-pattern.md` : 컴포넌트 폴더 내부 세그먼트(`api` / `ui` / `model` / `utils` / `types` / `constants` / `context`)를 어떻게 나누고 어디에 둘 것인가

## 핵심 원칙

모든 컴포넌트는 아래의 추상화 레벨을 지켜야 함.
핵심적으로 모든 컴포넌트는 도메인 로직을 다루는지에 따라 `modules/...` 또는 `shared/components/...`에 들어감.

`shared/components/` 내에서도 내부 로직·ui를 다루는 컴포넌트인지에 따라 `shared/components/composites/...`와 `shared/components/primitives/...`로 나뉨.

`modules` 컴포넌트 내에서도 section 단위의 독립적인 구획을 담당하는 큰 컴포넌트(`widgets`)와, 카카오 로그인 버튼처럼 섹션이 되지 못하는 작은 컴포넌트(`features`)로 나뉨.

### 컴포넌트 종류

- `modules/widgets` : `<section />`으로 분류할 수 있을 만큼 규모가 큰, 문서의 독립적인 구획을 담당하는 컴포넌트. 도메인 로직을 포함하며, 페이지에서 import되어 사용되고 **여러 page에서 재사용 가능**. 데이터 패칭부터 UI까지 하나의 End to End 플로우를 책임짐.
- `modules/features` : widgets 내부에서 독립적으로 존재할 수 있는, 섹션이 되지 못하는 작은 단위의 도메인 컴포넌트. 여러 widgets·pages에서 재사용될 수 있으며, 할당받은 완결된 작업을 스스로 수행.
- `shared/components/composites` : 공통 ui와 내부(ui) 로직을 다룸. 도메인 로직은 다루지 않음.
- `shared/components/primitives` : 공통 ui만 다룸.

### widgets vs features 판단 기준

**공통점**: 둘 다 도메인 로직을 가지며, 내부에 API 호출까지 갖추고 혼자서도 동작하는 독립적인 단위이므로 `index.tsx` 기준 props가 거의 없어야 함.

**판단 순서** (두 단계):

1. **섹션 단위인가?** `<section />`으로 묶일 만큼 큰 구획이면 widgets. 대체로 섹션의 제목이 H2이므로, Heading(H2)이 있으면 무조건 widgets, 없으면 widgets이 아닐 가능성이 높음.
2. **1번으로 애매하다면, widgets 안에서 재사용될 수 있는가?** 재사용 가능하면 features, 아니면 widgets.

두 단계로도 모든 UI를 나눌 수는 없으므로, 애매한 케이스는 그때마다 팀에서 논의.

### 참조 규칙

- 각 컴포넌트 추상화는 `modules` > `composites` > `primitives` 순서의 레벨을 가지며, 하위 레벨만 사용 가능. 상위 레벨 컴포넌트는 사용 불가.
  - 예: `composites`는 `primitives`를 조합해 구현 가능하지만, `primitives`는 `composites`를 사용할 수 없음.
  - `modules`는 `composites`와 `primitives`를 조합해 구현 가능하지만 역은 성립하지 않음.
- **widgets는 widgets를 호출할 수 없음.** `<section />` 안에 `<section />`이 호출되는 구조가 어색하기 때문.
- **features가 features를 호출하면 도메인이 섞이므로 금지.**
- 도메인이 겹치는 상황에서는 컴포넌트 재사용 대신, 도메인 로직을 훅으로 만들어 `shared/hooks/domain`으로 내려서 재사용.

### 주의사항

- 라우트에 대응하는 화면 단위는 `src/pages`에 두며, widgets를 import해서 조립하는 역할만 담당. UI 단위가 크지 않은 경우(예: 로그인 컴포넌트)에는 features 컴포넌트를 page에서 바로 import 가능.
- 하나의 페이지를 보여주는 컴포넌트는 `modules/`, `shared/components/`에 두지 않음. 해당 컴포넌트는 이 문서의 추상화 규칙을 따르지 않음.

#### modules/widgets

- 도메인 로직을 다루는 컴포넌트
- section 단위의 큰 컴포넌트로, 여러 page에서 재사용 가능
- 데이터 패칭 같은 로직을 수행하면서 하위 요소에 책임을 할당하는 지휘자 역할
- 패칭과 데이터, 액션 핸들러는 widgets가 책임지되, 책임이 너무 많아지면 `useXxx` 훅으로 책임별로 묶어 widgets 폴더에 코로케이션 // TODO feature도 페칭 가능
- 하위에 도메인별 디렉토리로 작성
  - e.g. `myPage/CustomerCenterSection`, `mountain/CourseDetailBottomSheetSection`

#### modules/features

- 도메인 로직(query, mutation, hooks/domain/...)을 다루는 컴포넌트
- 섹션이 되지 못하는 작은 단위로, 여러 widgets·pages에서 재사용 가능한 단위로 작성
  - e.g. TravelCalendar, ActiveMemberTab, DivisionSelector
- 하위에 도메인별 디렉토리로 작성
  - e.g. `auth/KakaoLoginButton`, `member/ActiveMemberTab`
- 여러 도메인에 걸치는 공통 컴포넌트(예: 투두캘린더)는 UI와 화면 전환 정도만 가지면 composites, 도메인 로직을 가지면 features로 분류

#### shared/components/composites

- 도메인을 다루지 않는 컴포넌트
- 내부 로직(ui 로직)까지만 다룸
- 탭 같은 UI는 상태 끌어올리기를 하지 않고, 컴파운드 패턴이나 FACC 패턴으로 composites 컴포넌트 자체가 상태와 UI 로직을 갖도록 함
- e.g. Calendar, Tab, SwitchCase, Selector

#### shared/components/primitives

- 내부 로직 및 도메인 로직을 다루지 않는 컴포넌트
- ui만 다루며, primitives끼리 조합한 결과도 primitives로 둠
- 종류에 따라 `ui`, `layout`, `animation`으로 나뉨
  - `ui` : 색상이나 모형 등 실체가 있는 컴포넌트
    - e.g. Button, Input, TextField, CalendarItem
  - `layout` : 특정 요소를 위치시키는 컴포넌트
    - e.g. Flex, Space, PositionBottom
  - `animation` : 특정 요소의 애니메이션을 다루는 컴포넌트
    - e.g. FadeIn, Slide

## 컴포넌트 구현하기

### 절차

1. 도메인 로직 확인하기

산행 기록 캘린더 컴포넌트를 만든다면 도메인 로직은 "산행 기록".

2. 추상화 레벨에 맞추어 구현 계획 세우기

산행 기록 캘린더 컴포넌트는 아래와 같이 추상화 가능.

- **산행 기록 캘린더 컴포넌트** → `modules/features`
- 날짜 변경·선택 등의 기능을 담은 **캘린더 컴포넌트** → `shared/components/composites`
- 캘린더 컴포넌트에서 사용된 **재사용 가능한 ui 컴포넌트** → `shared/components/primitives`
- 산행 기록 캘린더를 특정 페이지에서 section 단위로 사용하면 → `modules/widgets`

3. 구현하기

---

**기억할 것**: 이 문서의 추상화 규칙 외의 불필요한 추상화는 절대 금지.
