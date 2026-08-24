---
name: knot-issue-planning
description: Knot 저장소의 BE·FE GitHub Issue를 만들거나 초안·검토·정리할 때 공통 계약으로 위험도, 인터뷰, Grill, ADR 필요성과 짧은 세 섹션 본문을 검증한다. Issue 생성·등록·초안·검토 요청에 사용하고 확정 Issue 구현이나 일반 GitHub 조회에는 사용하지 않는다.
---

# Knot Issue Planning for Claude Code

작업 전에 [공통 Issue Planning 스킬](../../../.agents/skills/knot-issue-planning/SKILL.md)을
완전히 읽고 그 절차와 연결된 reference를 정본으로 따른다.

- 정본의 `$knot-deep-interview`는 `/knot-deep-interview`로 호출한다.
- 정본의 `$knot-grill-me`는 `/knot-grill-me`로 호출한다.
- 판정과 렌더링은 `python3 harness/issue_planning.py <snapshot.json> --pretty` 결과를 따른다.
- 구현 시작 시 필요한 ADR은
  `python3 harness/materialize_adr.py <snapshot.json> --implementation --pretty`로 현재 작업 브랜치에
  `Proposed`로 만든다.
- `requested_action=publish_issue`는 요청 의도일 뿐이며 `remote_write_authorized=false`인 동안
  GitHub 원격을 변경하지 않는다.
- 이 파일에 공통 정책을 복사하거나 Claude 전용 기준을 추가하지 않는다.
