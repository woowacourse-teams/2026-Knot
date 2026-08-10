# 명세 관리 원칙

이 디렉터리는 Knot 구현자가 어떤 문서를 기준으로 판단해야 하는지 안내합니다. Notion 원문을 무조건 복제하지 않고, 저장소에서 version control할 필요가 있는 계약과 원문 링크를 연결합니다.

## 기준 위치

| 명세 종류 | 기준 위치 | 저장소에서 관리할 내용 |
| --- | --- | --- |
| 제품 목표, 사용자 흐름, 기능 정책 | 팀 Notion / BE 위키 | 원문 링크, 마지막 확인일, 구현 관련 요약 |
| Issue별 범위와 완료 조건 | GitHub Issue | 목표, 범위, 제외 범위, 완료 조건, 의존성 |
| API 요청·응답 계약 | Swagger UI / OpenAPI | 코드와 함께 생성되는 API 문서 |
| DB 구조와 변경 | version-controlled DDL 또는 migration | 실행 순서와 검증 가능한 스키마 변경 |
| 기술 선택 | `docs/adr` | 배경, 선택, 대안, 결과 |
| 외부 API 원본 응답 사례 | 익명화된 fixture | 파서 테스트에 필요한 최소 JSON |

## 명세 링크 규칙

Issue에서 Notion 명세를 참조할 때 다음 정보를 남깁니다.

```markdown
- 명세: https://www.notion.so/...
- 상태: 확정 | 검토 중
- 마지막 확인일: YYYY-MM-DD
- 이번 Issue에서 사용하는 범위: ...
```

링크 접근 권한이 없는 팀원이나 도구가 작업할 가능성이 있으면, 비밀정보를 제외한 필수 결정과 완료 조건을 Issue에도 작성합니다.

## 충돌 처리

1. GitHub Issue와 Notion의 제품 동작이 다르면 제품 담당자에게 확인하고 Issue 완료 조건을 갱신합니다.
2. 구현과 기존 OpenAPI 계약이 다르면 구현을 기존 계약에 맞춥니다. 계약을 의도적으로 변경해야 할 때만 Issue 완료 조건과 PR 설명에서 변경을 승인받고, 구현과 OpenAPI 정의를 같은 PR에서 함께 수정합니다.
3. 구현 방향과 Accepted ADR이 다르면 기존 ADR을 몰래 수정하지 않고 새 ADR로 대체합니다.
4. 정책이 확정되지 않았으면 임의로 선택하지 않고 Issue를 `Blocked` 또는 `Backlog`로 이동합니다.

## 저장소에 포함하지 않는 정보

- OAuth client secret, access token, refresh token
- 개인 Notion 워크스페이스의 원본 내보내기
- 사용자 이름, 이메일 등 불필요한 개인 정보
- 개인 컴퓨터의 절대 경로와 로컬 설정
- 검증에 필요하지 않은 대용량 API 응답 전체

외부 API fixture가 필요하면 테스트 목적에 필요한 필드만 남기고 식별자와 개인정보를 익명화합니다.
