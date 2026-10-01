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
`requested_action=publish_issue`만으로는 원격 쓰기 권한이 아니다. 현재 사용자가 생성을
명시하고 계약이 통과한 경우에만 `--publish --repo OWNER/REPO`를 사용한다. ADR
materializer에는 `--issue-number`로 실제 GitHub Issue 번호를 전달한다.

## 작업 진행 방식

요청이 애매하거나 사용자의 판단이 꼭 필요한 결정이 아니면 확인 질문 없이 끝까지 진행한다.
중간 변경마다 허락을 구하지 않고, 끝난 뒤 무엇을 바꿨는지 보고한다. 원격 쓰기(Issue 게시,
commit, push, PR, merge)는 위 `AGENTS.md`의 권한 규칙을 그대로 따른다.

## 문서 작성 형식

노션 페이지, 인터뷰 질문지, 문서 초안 등 어떤 문서화 작업에서도 ①②③ 같은 원 숫자 기호를
쓰지 않는다. 순서가 필요하면 `1.` 번호 목록이나 일반 숫자를 쓴다.
