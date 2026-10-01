@AGENTS.md

# Claude Code

Knot 저장소의 BE·FE Issue 기획에는 `/knot-issue-planning`을 사용한다. 자료가 부족한
고위험 작업의 인터뷰에는 `/knot-deep-interview`를 사용하고, 모든 고위험 계약의 압박
검증에는 `/knot-grill-me`를 사용한다.

`.claude/skills`는 Claude Code용 진입점이다. 백엔드 전용 스킬(`knot-commit`, `knot-pr`,
`knot-api-spec`)의 진입점은 `backend/.claude/skills`이고 정본은 `backend/.agents/skills`다.
백엔드 작업은 `backend/`에서 시작해야 두 진입점을 함께 발견한다. 판단 규칙의 정본은 `.agents/skills`와
`docs/harness/issue-planning.md`이며, 실행 결과는 Codex와 동일한
`harness/issue_planning.py`와 `harness/materialize_adr.py`로 검증한다. Claude 전용 규칙을
복사해 별도의 정책 정본을 만들지 않는다.

정본 문서의 `$knot-*` 표기는 Claude Code에서 같은 이름의 `/knot-*` 스킬 호출로 해석한다.
`requested_action=publish_issue`만으로는 원격 쓰기 권한이 아니다. 현재 사용자가 생성을
명시하고 계약이 통과한 경우에만 `--publish --repo OWNER/REPO`를 사용한다. ADR
materializer에는 `--issue-number`로 실제 GitHub Issue 번호를 전달한다.

## 질문 기준

요청을 받으면 확인 질문 없이 끝까지 처리한다. 다음 경우에만 사용자에게 묻고, 선택지가 있으면
`AskUserQuestion`의 객관식으로 받는다.

- 사용자만 내릴 수 있는 결정이 필요한 경우
- 요청끼리 또는 요청과 저장소 지침이 충돌하는 경우
- 요청이 애매해 해석에 따라 결과가 크게 달라지는 경우

관례나 합리적 기본값으로 정할 수 있는 선택은 묻지 않고 정한 뒤 결과에 짧게 남긴다. 위 질문 기준은
AGENTS.md의 쓰기 권한 규칙(commit, push, PR, merge, Issue 게시는 현재 요청에서 명시한 경우에만
수행)을 바꾸지 않는다.
