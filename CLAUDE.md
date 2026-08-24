@AGENTS.md

# Claude Code

Knot 저장소의 BE·FE Issue 기획에는 `/knot-issue-planning`을 사용한다. 자료가 부족한
고위험 작업의 인터뷰에는 `/knot-deep-interview`를 사용하고, 모든 고위험 계약의 압박
검증에는 `/knot-grill-me`를 사용한다.

`.claude/skills`는 Claude Code용 진입점이다. 판단 규칙의 정본은 `.agents/skills`와
`docs/harness/issue-planning.md`이며, 실행 결과는 Codex와 동일한
`harness/issue_planning.py`와 `harness/materialize_adr.py`로 검증한다. Claude 전용 규칙을
복사해 별도의 정책 정본을 만들지 않는다.

정본 문서의 `$knot-*` 표기는 Claude Code에서 같은 이름의 `/knot-*` 스킬 호출로 해석한다.
테스트 단계의 `requested_action=publish_issue`를 원격 쓰기 권한으로 해석하지 않는다.
ADR materializer에는 `--issue-number`로 실제 GitHub Issue 번호를 전달한다.
