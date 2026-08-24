---
name: knot-grill-me
description: Knot 저장소의 BE·FE 고위험 Issue 계약을 구현 전에 압박 검증해 실패·복구·보안·데이터·FE-BE 계약의 허점을 찾고 Pass 또는 Hold로 판정한다. 고위험 인터뷰가 완료 또는 근거 충분으로 생략됐거나 사용자가 Grill 검증을 요청할 때 사용한다.
---

# Knot Grill Me for Claude Code

작업 전에 [공통 Grill Me 스킬](../../../.agents/skills/knot-grill-me/SKILL.md)을 완전히 읽고
그 절차와 연결된 checklist를 정본으로 따른다.

- 인터뷰 근거 계약을 반복 요약하지 말고 가장 치명적인 허점부터 확인한다.
- 치명적인 미결정이 남으면 `Hold`, 아니면 `Pass`로 반환한다.
- 결과를 `/knot-issue-planning`의 내부 snapshot으로 반환한다.
