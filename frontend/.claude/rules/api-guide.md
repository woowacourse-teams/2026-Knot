---
description: API 요청 관련 가이드라인
paths:
  - "src/shared/api/**/*"
---

# api 가이드라인

## 디렉토리 구조

API 관련 코드는 한곳에서 계층적으로 관리해야 하므로 모두 `src/shared/api` 안에 둠. 쿼리·뮤테이션 훅도 별도 훅 폴더로 빼지 않고 api 폴더 안에 유지.

- `httpClient/` — HTTP 클라이언트(axios) 인스턴스. 인증 토큰 처리·공통 에러 정규화 같은 횡단 관심사는 인터셉터에서 처리.
- `fetch/` — 순수 함수인 fetch를 엔드포인트별 폴더 구조로 관리. restful API 요청 엔드포인트와 `fetch/` 하위 디렉토리 위치가 일치해야 함.
  - e.g. `GET /api/v1/users/[id]` → `src/shared/api/fetch/api/v1/users/[id]/index.ts`
  - 폴더 이름의 `fetch`는 네이티브 fetch API가 아니라 **요청 함수**를 뜻함. 실제 요청은 `httpClient/`의 인스턴스로 보냄.
- `queryKey/` — 쿼리 키는 뮤테이션에서도 쓰이므로 별도 폴더로 분리, `user.ts`처럼 도메인별 파일로 관리.
- `queries/`, `mutations/`, `suspense/`, `prefetch/` — 쿼리·뮤테이션·서스펜스·프리페치 훅을 각각 둠. 작성 규칙은 `.claude/rules/query-hooks.md` 참고.

## API 요청(fetch) 로직

API 요청 로직은 엔드포인트 폴더의 index.ts 내에 위치하며, 형식은 아래와 같이 작성.

```typescript
// src/shared/api/fetch/api/v1/facilities/index.ts
import instance from "@/shared/api/httpClient";
import type { FacilityMarker } from "@/shared/types/map";

/**
 * @public
 * @category Constants
 * @description 편의시설 목록 조회 API 경로를 생성하는 함수
 * @param mountainId - 산 ID
 * @returns API 경로 문자열
 */
export const FACILITY_API_PATH = (mountainId: string) =>
  `/api/v1/facilities?mountainId=${mountainId}`;

/**
 * @public
 * @category Types
 * @interface GetFacilitiesApiResponse
 * @description 편의시설 목록 조회 응답 타입
 * @property {string} mountainId - 산 ID
 * @property {FacilityMarker[]} facilities - 편의시설 마커 목록
 */
export interface GetFacilitiesApiResponse {
  mountainId: string;
  facilities: FacilityMarker[];
}

/**
 * @public
 * @category Facilities
 * @description 특정 산의 편의시설 목록을 조회합니다 (화장실, 주차장 등)
 * @param mountainId - 산 ID
 * @returns 편의시설 목록
 * @example
 * const result = await getFacilitiesApi("mountain123");
 * console.log(result.facilities); // FacilityMarker[]
 */
export const getFacilitiesApi = async (mountainId: string) => {
  const response = await instance<GetFacilitiesApiResponse>({
    method: "get",
    url: FACILITY_API_PATH(mountainId),
  });

  return response.data;
};
```

형식 설명

- API 경로 생성 함수·상수는 `FACILITY_API_PATH`처럼 대문자와 \_로 작성.
- API 응답 타입 이름은 `GetFacilitiesApiResponse`처럼 메서드 형식 + API 요청 로직 이름 + ApiResponse 조합으로 작성.
- API 요청 함수 이름은 `getFacilitiesApi`처럼 API 요청 목적 + 메서드 형식 + Api 조합으로 작성.
- API 요청 함수는 async 함수로 작성하며, 요청 성공 시 응답 데이터만 반환.
- API 요청 함수에는 JSDoc 주석 필수. @description, @param, @returns, @example 태그로 목적·매개변수·반환값·사용 예시를 명확히 설명.
