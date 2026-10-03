---
name: knot-pr
description: “PR 본문 작성해줘”, “PR 준비 상태 확인해줘”, “PR 생성해줘”, “PR 올려줘”처럼 Knot 백엔드의 실제 git diff와 연결된 GitHub Issue로 PR을 작성·검증·게시할 때 사용한다. 초안 요청은 게시 권한이 아니며 실제 게시도 명시적 요청에만 수행한다. commit, push, Issue 수정, merge에는 사용하지 않는다.
---

# knot-pr for Claude Code

작업 전에 [백엔드 PR 스킬](../../../.agents/skills/knot-pr/SKILL.md)을 완전히 읽고
그 절차를 정본으로 따른다.

- 정본의 `$knot-*` 표기는 같은 이름의 Claude Code 스킬 호출로 해석한다.
- 이 파일에 공통 정책을 복사하거나 Claude 전용 기준을 추가하지 않는다.
