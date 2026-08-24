# Knot Issue·ADR 하네스

- 적용 범위: 저장소 전역, BE·FE 공통
- 상태: 테스트
- 원격 변경: 비활성화

이 하네스는 깊이 있는 내부 계약을 검증한 뒤 팀의 기존 세 섹션으로 짧은 GitHub Issue
후보를 만든다. 현재 테스트 버전은 Issue 본문과 판정 결과를 생성하지만 GitHub에는
게시하지 않는다.

## 요청 방법

실제 생성을 의도한 표현은 다음과 같다.

```text
로그인·회원가입 GitHub Issue 만들어줘
이 기능을 Issue로 등록해줘
```

초안만 필요한 표현은 다음과 같다.

```text
로그인 Issue 초안 잡아줘
이 요구사항을 Issue 형식으로 검토해줘
```

테스트 버전에서는 두 요청 모두 원격 변경 없이 `action=render_draft`로 결과만 보여준다.
생성 요청은 `requested_action=publish_issue`, `publish_ready=true`로 의도를 구분한다.
`remote_write_authorized`는 항상 `false`이며 다른 필드는 쓰기 권한이 아니다.

## 동작

```text
요청 권한 판정
→ 저장소·문서·코드 조사
→ 위험 분류
→ 저위험: 최소 계약
→ 고위험: Deep Interview → Grill Me → Pass/Hold → ADR 판단
→ 결정적 검증기
→ 세 섹션 dry-run Issue 본문
```

`Hold`이면 Issue 후보를 만들지 않고 누락 항목과 재개 조건을 보여준다. `Pass`이면
계약 식별자와 Issue 본문을 보여준다.

Issue 본문은 다음 형태만 사용한다.

```markdown
## 구현 기능 설명

## TODO

## 메모
```

내부 snapshot의 범위, 실패·복구 흐름, 완료 조건과 검증 근거는 판정에 사용하되 본문에
그대로 펼치지 않는다. ADR이 필요하면 `구현 기능 설명`에 구체적인 문제 상황과 목표를
1~2문장으로 적고, `메모`에는 결정 한 줄과 예정 경로만 적는다. ADR이 필요하지 않으면
평소의 간단한 Issue로 남긴다.

snapshot은 저장소 밖의 OS 임시 파일에만 만들고 현재 사용자만 읽을 수 있게 제한한다.
판정 또는 ADR 생성이 끝나면 성공·실패와 관계없이 삭제한다. 인터뷰 원문과 비밀값은
snapshot이나 저장소 문서에 남기지 않는다.

## ADR 판단과 자산화

다음 세 조건을 모두 만족할 때만 ADR이 필요하다.

1. 팀이 실제로 검토한 현실적인 대안이 둘 이상이다.
2. 제품 정책, 아키텍처, 보안, 데이터 또는 공용 워크플로우에 장기간 영향을 준다.
3. 후속 Issue와 구현자가 같은 결정을 반복해서 참조한다.

AI는 대안이 없었다면 없다고 답하도록 안내한다. 한 번 더 현상 유지나 반대 방향의 검토
여부를 확인한 뒤에도 대안이 없으면 ADR을 만들지 않으며, 새 대안을 지어내지 않는다.

Issue 단계에서는 ADR 파일을 만들지 않는다. 실제 구현을 시작한 작업 브랜치에서 다음
명령으로 `Proposed` 파일을 만든다.

```bash
python3 harness/materialize_adr.py <snapshot.json> --implementation --pretty
```

`--implementation`은 Issue 기획 단계의 실수로 파일이 생기는 것을 막는 실행 단계
표시다. 파일은 코드와 같은 PR에 포함하고 팀 리뷰 후에만 `Accepted`로 바꾼다. 두 Python
스크립트는 commit, push, Issue 게시와 PR merge를 실행하지 않는다.

## 팀 공통 적용과 프론트엔드 하네스

Issue #167의 이 하네스는 저장소 공통 Issue 기획, 위험 분류와 ADR 생명주기를 담당한다.
Issue #165의 프론트엔드 공통 하네스는 `frontend/` 코드 구현·테스트·리뷰 규칙을 담당한다.

프론트엔드 작업에서는 다음 순서로 적용한다.

1. Issue 기획에는 루트 `AGENTS.md`, `CLAUDE.md`와 이 공통 하네스를 적용한다.
2. 구현에는 `frontend/` 아래의 더 구체적인 지침을 추가로 적용한다.
3. 하위 지침이 공통 Issue 안전 계약을 약화하면 자동 선택하지 않고 충돌을 보고한다.

## 적용 사례

- [회원가입 Issue·ADR 하네스 적용 사례](signup-issue-adr-walkthrough.md): 실제 요구사항
  대화와 ADR을 설명하기 위한 가상 추가 대화를 구분해 보여준다.

## 현재 범위

- Issue 생성 전 계약 검증
- 저위험·고위험 routing
- Deep Interview와 Grill Me
- ADR 필요 여부와 채택안
- 원격 쓰기 권한 판정
- 중복 방지용 안정적인 계약 식별자 생성
- 구현 브랜치의 안전하고 멱등적인 `Proposed` ADR 파일 생성
- GitHub Actions의 결정적 하네스 테스트

Codex는 `.agents/skills`, Claude Code는 `.claude/skills`에서 이 공통 계약과 스크립트를
참조한다. 도구별 지침은 실행 진입점만 제공하며 판단 규칙을 복사해 별도 정본으로 만들지
않는다.

Commit hook, AI PR gate와 추가 Merge gate는 포함하지 않는다. 하네스 전용 CI를 추가하되
기존 Governance와 Backend CI는 변경하지 않는다.
