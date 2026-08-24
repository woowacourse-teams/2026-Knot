---
name: knot-deep-interview
description: Knot 저장소의 BE·FE 고위험 Issue 기획에서 현재 맥락, 구체적인 문제 상황, 결정 필요성, 실제 대안, 선택 이유와 재논의 조건을 한 질문씩 확인한다. knot-issue-planning이 고위험으로 분류했거나 사용자가 인터뷰를 요청할 때 사용한다.
---

# Knot Deep Interview for Claude Code

작업 전에 [공통 Deep Interview 스킬](../../../.agents/skills/knot-deep-interview/SKILL.md)을
완전히 읽고 그 절차와 연결된 question contract를 정본으로 따른다.

- 한 번에 질문 하나만 한다.
- 사용자가 대안이 없다고 답할 수 있게 한다.
- 실제로 논의하지 않은 대안을 만들거나 확인된 대안으로 기록하지 않는다.
- 결과를 `/knot-issue-planning`의 내부 snapshot으로 반환한다.
