# Knot 저장소 공통 작업 지침

## 적용 범위와 우선순위

이 지침의 Issue 기획·검증·ADR 생명주기는 저장소 전역과 BE·FE 작업에 공통으로 적용한다.
하위 디렉터리의 지침은 해당 코드의 구현 규칙을 추가할 수 있지만, 공통 Issue 템플릿,
원격 쓰기 차단, 실제 대안 확인과 ADR 생성 조건을 약화할 수 없다. 충돌하면 임의로 한쪽을
선택하지 말고 충돌과 영향을 보고한다.

- Issue #167은 저장소 공통 Issue·ADR 기획 계약을 담당한다.
- Issue #165의 프론트엔드 하네스는 `frontend/` 구현·리뷰 규칙을 담당한다.
- 프론트엔드 Issue 기획에는 이 공통 계약을 먼저 적용하고, 실제 구현에는 #165의
  `frontend/` 지침을 함께 적용한다.

## Issue 기획

Knot의 GitHub Issue 생성·초안·검토 요청에는 `$knot-issue-planning`을 사용한다. 운영
규칙은 `docs/harness/issue-planning.md`를 읽는다.

현재 하네스는 테스트 단계다. `Issue 만들어줘` 요청도 dry-run까지만 수행한다.
`requested_action=publish_issue`는 사용자의 의도일 뿐 쓰기 권한이 아니다. 판정기의
`remote_write_authorized`는 항상 `false`이며 `gh issue create`, `gh issue edit`와 Project
변경을 실행하지 않는다. Issue 기획 중에는 branch, commit과 ADR 파일도 변경하지 않는다.

## Issue 구현

확정 Issue 구현을 시작할 때 Issue 본문을 먼저 읽는다. `메모`에 ADR 결정과 예정 경로가
있고 파일이 없다면 검증된 결정 snapshot으로
`python3 harness/materialize_adr.py <snapshot.json> --implementation --pretty`를 실행한다.
ADR은 구현 브랜치에 `Proposed`로 만들고 코드와 같은 PR에 포함한다. 원래 snapshot을
복구할 수 없으면 현재 상황, 실제로 논의한 대안과 선택 이유만 다시 확인하며 대안을
추측하지 않는다. 팀 리뷰가 승인한 뒤에만 `Accepted`로 바꾸며 commit, push와 merge는
사용자가 명시적으로 요청한 범위에서만 수행한다.

Issue에는 기존 템플릿의 `구현 기능 설명`, `TODO`, `메모`만 사용한다. 전체 내부 계약,
ADR 전문과 인터뷰 원문은 Issue에 넣지 않는다.

## 임시 snapshot 안전 규칙

Issue 계약 snapshot은 저장소 밖의 OS 임시 파일에만 쓰고 권한을 현재 사용자로 제한한다.
판정이나 ADR 생성이 끝나면 성공·실패와 관계없이 즉시 삭제한다. 토큰, 비밀번호,
개인정보와 비공개 대화 원문은 snapshot이나 문서에 넣지 않는다.

## 전달 규칙

Issue와 PR 컨벤션은 `CONTRIBUTING.md`와 `.github/knot-conventions.yml`을 따른다. 기존
Governance, Backend CI와 Project 자동화를 유지한다.

사용자의 기존 변경을 보존한다. 원격 쓰기, branch, commit, push, PR과 merge 권한은
요청마다 별도로 판정한다.
