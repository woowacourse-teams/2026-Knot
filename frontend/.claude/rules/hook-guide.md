---
description: React hook 가이드라인
paths: ["src/shared/hooks/**/*.ts"]
---

# react hook 가이드라인

## 개요

React hook 작성 시 지켜야 하는 가이드라인.

## 핵심 개념

`shared/hooks`의 커스텀 훅은 도메인 로직을 다루는 훅(domain 훅)과 다루지 않는 훅(common 훅)으로 나뉨.

e.g.

- useHaptic, useSMS → common 훅
- useGetCoursePath, useDrawPath → domain 훅

단, 특정 컴포넌트와 강결합된 훅은 `shared/hooks`에 두지 않고 해당 컴포넌트 폴더의 `model`에 코로케이션. **훅의 위치는 사용 횟수("지금은 여기서만 쓴다")가 아니라 컴포넌트와의 강결합 여부로 판단**하며, 세부 기준은 `.claude/rules/shared-layer.md`의 "코드를 shared로 내리는 기준" 참고. 덕분에 재사용 가능한 훅을 찾을 때는 `shared/hooks`만 확인하면 됨.

쿼리·뮤테이션 훅은 `shared/hooks`가 아니라 `shared/api`의 `queries`/`mutations`에서 관리. (`.claude/rules/query-hooks.md` 참고)

## 훅 파일 구조

모든 훅은 index.ts 파일로 작성.
훅 이름과 동일한 디렉토리를 만들고, 그 안에 index.ts 파일을 작성하는 형태.

## domain 훅

도메인 로직을 다루되 특정 컴포넌트에 강결합되지 않은 훅은 domain 훅으로 분류. 도메인이 겹치는 상황에서는 컴포넌트 재사용 대신 도메인 로직을 훅으로 만들어 `shared/hooks/domain`으로 내려서 재사용.

domain 훅은 common 훅에 도메인만 붙인 형태. (e.g. common 훅 `useGetPath`에 course 도메인을 붙이면 domain 훅 `useGetCoursePath`)

domain 훅은 `src/shared/hooks/domain/` 디렉토리에 **도메인별 디렉토리로 묶어서** 작성.

e.g.

- src/shared/hooks/common/useHaptic/index.ts

- src/shared/hooks/domain/course/useGetCoursePath/index.ts
- src/shared/hooks/domain/map/useDrawPath/index.ts
- src/shared/hooks/domain/map/useDrawMarkers/index.ts

## checklist

- [ ] 커스텀 훅은 index.ts 파일로 작성
- [ ] 훅 이름과 동일한 디렉토리를 만들고 그 안에 index.ts를 작성
- [ ] 도메인 로직을 다루면 domain 훅, 아니면 common 훅으로 분류
- [ ] domain 훅은 common 훅에 도메인만 붙인 형태
- [ ] domain 훅은 `src/shared/hooks/domain/`에 도메인별 디렉토리로 묶어서 작성
- [ ] 특정 컴포넌트와 강결합된 훅은 `shared/hooks`가 아니라 해당 컴포넌트 폴더의 `model`에 코로케이션
