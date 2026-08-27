---
name: knot-deep-interview
description: Knot 저장소의 BE·FE 고위험 Issue 기획에서 자료가 누락·충돌했거나 현재 유효성이 불명확할 때 현재 맥락, 구체적인 문제 상황, 결정 필요성, 실제 대안과 선택 이유를 한 질문씩 확인한다. knot-issue-planning의 근거 판정에 미결정이 남았거나 사용자가 인터뷰를 요청할 때 사용한다.
---

# Knot Deep Interview for Claude Code

작업 전에 [공통 Deep Interview 스킬](../../../.agents/skills/knot-deep-interview/SKILL.md)을
완전히 읽고 그 절차와 연결된 question contract를 정본으로 따른다.

- 한 번에 질문 하나만 한다.
- 사용자가 인터뷰를 명시적으로 요청하지 않았고 공통 계약이 근거 충분으로 판정한 작업은
  인터뷰를 시작하지 않는다.
- 사용자가 대안이 없다고 답할 수 있게 한다.
- 실제로 논의하지 않은 대안을 만들거나 확인된 대안으로 기록하지 않는다.
- 결과를 `/knot-issue-planning`의 내부 snapshot으로 반환한다.
