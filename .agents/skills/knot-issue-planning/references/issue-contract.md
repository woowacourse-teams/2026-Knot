# Issue 계약 snapshot

`harness/issue_planning.py`에 전달할 최소 JSON 구조다. snapshot 최상위는 객체여야 한다.
문자열은 공백이 아닌 값을, 목록은 공백이 아닌 문자열만 사용한다. 사용자 입력에는
Issue의 세 섹션을 깨뜨리는 Markdown H2(`## `)를 넣지 않는다.

```json
{
  "operation": "draft 또는 create",
  "title": "[BE] 작업 제목",
  "purpose": "해결할 문제와 결과",
  "scope": ["포함 범위"],
  "acceptance_criteria": ["관찰 가능한 완료 조건"],
  "verification": ["검증 방법"],
  "evidence": ["관련 코드·문서·Issue"],
  "risk_signals": ["security"],
  "non_goals": ["제외 범위"],
  "normal_flows": ["정상 흐름"],
  "failure_flows": ["실패·취소·재시도 흐름"],
  "recovery_flows": ["복구 흐름"],
  "impacts": ["FE·BE·데이터·외부 영향"],
  "dependencies": [],
  "residual_risks": [],
  "grill": {
    "status": "pass",
    "resolved_questions": ["해결한 차단 질문"]
  },
  "adr": {
    "required": true,
    "status": "proposed",
    "decision": "한 줄 결정",
    "context": "논의가 시작된 배경과 제약",
    "situation": "누가 언제 어떤 문제를 겪었는지 보여주는 구체적 상황",
    "decision_drivers": ["판단 기준"],
    "alternatives": ["대안과 트레이드오프"],
    "alternatives_confirmed": true,
    "long_term_impact": true,
    "future_reference": true,
    "rationale": "채택 이유",
    "consequences": ["긍정·부정 결과"],
    "revisit_when": ["재논의 조건"],
    "slug": "auth-policy",
    "decision_makers": "Knot 팀"
  }
}
```

ADR이 필요하지 않으면 다음 구조를 사용한다.

```json
{
  "required": false,
  "reason": "장기 결정이 아닌 이유"
}
```

`required=true`는 실제로 논의한 대안이 둘 이상이고, `long_term_impact`와
`future_reference`가 모두 `true`일 때만 사용한다. `alternatives_confirmed=true`는 사용자가
대안 목록이 실제 논의 내용이라고 확인했다는 뜻이다. AI가 제안한 선택지를 이 목록에
섞지 않는다. `slug`는 소문자 영문, 숫자와 하이픈만 사용한다. `decision_makers`는 ADR
파일에 표시할 선택 필드이며 생략하면 `Knot 팀`을 사용한다.

새 Issue를 기획할 때는 아직 번호를 알 수 없으므로 snapshot에 실제 ADR 경로를 만들지
않는다. Issue 본문에는 `docs/adr/{ISSUE_NUMBER}-<slug>.md`를 표시하고, Issue가 생성된 뒤
materializer에 `--issue-number <실제 번호>`를 전달해 경로를 확정한다. 기존 Issue를
다루거나 snapshot을 복구할 때는 최상위 `issue_number`를 양의 정수로 넣을 수 있다.
선택 필드 `adr.planned_path`를 함께 넣으면 번호와 slug가 모두 일치해야 한다.

snapshot은 저장소에 저장하지 않는다. OS 임시 파일을 현재 사용자만 읽을 수 있게 만들고
판정 또는 ADR 생성이 끝나면 성공·실패와 관계없이 삭제한다. 인터뷰 원문, 토큰,
비밀번호와 개인정보를 넣지 않는다.

현재 테스트 버전의 결과 필드는 다음 의미를 가진다.

- `action`: 실제 수행한 동작이며 통과한 계약은 `render_draft`다.
- `requested_action`: `operation=create`이면 `publish_issue`, 초안이면 `render_draft`다.
- `publish_ready`: 생성 의도로 들어온 Issue 후보 계약이 통과했는지 나타낸다. ADR 실제
  경로가 확정됐다는 뜻은 아니다.
- `remote_write_authorized`: 항상 `false`다. 다른 필드를 원격 쓰기 권한으로 해석하지 않는다.
- `adr_path_status`: 실제 Issue 번호 전이면 `pending_issue_number`, 확정 뒤에는 `finalized`다.
- `next_after_issue_created`: 번호 확정이 필요하면 `finalize_adr_path`다.
