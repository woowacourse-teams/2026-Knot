---
name: knot-commit
description: “커밋해줘”, “작업 단위로 커밋해줘”, “커밋 메시지 작성해줘”처럼 Knot 백엔드의 실제 git diff를 원자적 커밋으로 계획·검토·생성할 때 사용한다. 메시지 초안은 commit 권한이 아니며 실제 commit은 명시적 요청에만 수행한다. push, PR 게시, merge에는 사용하지 않는다.
---

# knot-commit for Claude Code

작업 전에 [백엔드 커밋 스킬](../../../.agents/skills/knot-commit/SKILL.md)을 완전히 읽고
그 절차를 정본으로 따른다.

- 정본의 `$knot-*` 표기는 같은 이름의 Claude Code 스킬 호출로 해석한다.
- 이 파일에 공통 정책을 복사하거나 Claude 전용 기준을 추가하지 않는다.
