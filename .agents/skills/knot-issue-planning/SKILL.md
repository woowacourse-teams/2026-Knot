---
name: knot-issue-planning
description: Knot 저장소의 BE·FE GitHub Issue를 만들거나 초안·검토·정리할 때 저장소 근거를 조사하고 위험도를 분류한 뒤, 고위험 작업의 결정 근거가 누락·충돌했거나 현재 유효성이 불명확하면 요구사항 인터뷰를 수행하고 Grill Me와 ADR 판단을 적용해 실행 가능한 공통 Issue 계약을 만든다. “Issue 만들어줘”, “Issue로 등록해줘”, “Issue 초안 잡아줘”, “요구사항을 Issue 형식으로 검토해줘” 요청에 사용한다. 확정 Issue의 코드 구현, PR 작성, 일반적인 GitHub 조회에는 사용하지 않는다.
---

# Knot Issue Planning

Issue 생성 요청을 저장소 전역의 실행 가능한 작업 계약으로 바꾼다. 현재 버전은 테스트
전용이므로 GitHub 원격을 변경하지 않고 dry-run 결과까지만 만든다.

## 1. 요청 권한 판정

1. `Issue 만들어줘`, `GitHub Issue로 등록해줘`처럼 명시적 생성 요청이면
   `operation=create`로 분류한다.
2. `초안`, `검토`, `정리`, `Issue 형식으로 보여줘` 요청이면 `operation=draft`로
   분류한다.
3. 두 의미가 섞였으면 원격 변경을 허용하지 않는 `draft`를 기본값으로 사용하고 사용자에게
   생성 여부를 확인한다.
4. 테스트 버전에서는 `create`도 계획 결과만 출력한다. `gh issue create`, `gh issue edit`,
   Project 변경을 실행하지 않는다.

## 2. 근거 조사

1. 저장소의 `AGENTS.md`와 `docs/harness/issue-planning.md`를 읽는다.
2. 요청과 관련된 제품 문서, ADR, 코드, 테스트와 기존 Issue를 읽는다.
3. 저장소에서 확인한 사실과 사용자가 결정해야 하는 제품 판단을 분리한다.
4. 확인 가능한 사실을 사용자에게 질문하지 않는다.
5. 현재 맥락, 구체적인 문제 상황, 선택 필요성, 실제 대안, 최종 선택과 선택 이유마다
   확인 내용과 출처를 기록한다.

## 3. 위험 분류

`references/risk-policy.md`를 읽고 위험 신호를 기록한다.

- 신호가 없으면 Lightweight 경로를 사용한다.
- 신호가 하나라도 있으면 Harnessed 경로를 사용한다.
- 경계 사례만 사용자에게 위험 분류를 확인한다.

## 4. 요구사항 구성

Lightweight 경로에서는 목적, 간결한 TODO, 완료 조건, 검증 방법과 근거를 작성한다.

Harnessed 경로에서는 먼저 여섯 가지 결정 정보의 근거를 판정한다.

1. 모든 정보가 출처에 명시돼 있고 자료 간 충돌이 없으며 현재도 유효하면 인터뷰를
   `skipped`로 기록한다. 항목별 확인 내용과 출처를 snapshot에 남긴다.
2. 하나라도 누락됐거나 자료가 충돌하거나 현재 유효성이 불명확하면
   `$knot-deep-interview`로 해당 판단을 한 질문씩 확인한다. 해소한 질문을 요약해
   인터뷰를 `completed`로 기록한다.
3. AI의 추론으로 빈 판단을 채우거나 출처 없는 생략을 선언하지 않는다.
4. 사용자에게 `자료 충분으로 인터뷰 생략` 또는 `사용자 확인으로 인터뷰 완료`를
   명시한다.

인터뷰 계약이 완성되면 `$knot-grill-me`로 실패 흐름과 숨은 가정을 압박 검증한다.
치명적인 미결정이 남으면 `Hold`로 판정한다.

## 5. ADR 판단

`references/adr-policy.md`를 읽고 실제 대안, 장기 영향과 반복 참조 여부를 모두 확인해
ADR 필요 여부를 판정한다.

- 필요하면 결정 전문을 내부 계약에 유지하고 Issue `메모`에는 결정 한 줄과 예정 경로만
  넣는다. 새 Issue의 번호가 정해지기 전에는 실제 파일 경로 대신
  `docs/adr/{ISSUE_NUMBER}-<slug>.md`를 표시한다.
- 실제로 논의한 대안이 없으면 AI가 대안을 만들지 않고 ADR이 필요 없다고 판정한다.
- 미확인 법·보안·개인정보 사실에 의존하면 추측하지 않고 `Hold`로 판정한다.
- Issue 단계에서는 ADR 파일을 만들지 않는다.

## 6. 결정적 판정

1. `references/issue-contract.md`의 JSON 구조로 저장소 밖의 OS 임시 snapshot을 만든다.
   파일 권한은 현재 사용자로 제한하고 종료 시 항상 삭제하도록 정리 절차를 먼저 건다.
2. 저장소 루트에서 다음 명령을 실행한다.

   ```bash
   python3 harness/issue_planning.py <snapshot.json> --pretty
   ```

3. 종료 코드가 0이 아니거나 결과가 `hold`면 누락 필드와 재개 조건을 보고하고 Issue
   생성을 제안하지 않는다.
4. 결과가 `pass`면 `issue_body`와 `contract_id`를 보여준다.
5. 고위험 결과의 `interview_status`와 `interview_notice`를 그대로 보여준다.
6. Issue 본문은 기존 템플릿의 `구현 기능 설명`, `TODO`, `메모` 세 섹션만 사용한다.
   내부 계약의 전문을 Issue에 복사하지 않는다.
7. 현재 버전의 실제 `action`은 항상 `render_draft`다. `operation=create`의 결과에는
   `requested_action=publish_issue`와 `publish_ready=true`가 표시되지만 이는 요청 의도와
   계약 통과 여부일 뿐 원격 쓰기 권한이 아니다.
8. `remote_write_authorized=false`를 확인하고 실제 게시 없이 종료한다.
9. ADR이 필요하고 `adr_path_status=pending_issue_number`면 Issue 생성 뒤 실제 번호로
   경로를 확정해야 한다고 보고한다.
10. 성공·실패와 관계없이 임시 snapshot을 삭제한다.

## 7. 구현 시작 시 ADR 자산화

ADR이 필요한 확정 Issue의 구현을 시작했고 예정 파일이 없다면 같은 작업 브랜치에서
실제 Issue 번호와 검증된 snapshot을 사용해 다음 명령을 실행한다.

```bash
python3 harness/materialize_adr.py <snapshot.json> \
  --issue-number <actual-issue-number> --implementation --pretty
```

생성한 ADR은 `Proposed` 상태로 코드와 같은 PR에 포함한다. 팀 리뷰가 승인한 뒤에만
`Accepted`로 바꾸며 AI는 commit, push, PR merge 권한을 추정하지 않는다. 원래 snapshot을
복구할 수 없으면 대안을 추측하지 말고 필요한 결정 정보만 다시 확인한다. ADR 생성 뒤에도
임시 snapshot을 즉시 삭제한다.

## 완료 기준

- 위험 분류 근거가 있다.
- 고위험 계약은 인터뷰 완료 또는 항목별 근거가 있는 생략 상태다.
- 인터뷰 생략·완료 상태를 사용자에게 알렸다.
- 계약 판정기의 `status`, `action`, `requested_action`, `remote_write_authorized`,
  `contract_id`를 보고했다.
- ADR 경로가 번호 확정 전인지 실제 Issue 번호로 확정됐는지 보고했다.
- `hold`의 누락 항목 또는 `pass`의 Issue 본문을 사용자가 확인할 수 있다.
- Issue 기획에서는 GitHub, branch, commit과 ADR 파일을 변경하지 않았다.
- 구현 요청에서는 필요한 ADR 파일이 `Proposed`로 현재 작업 브랜치에 있고 코드와 같은
  PR의 검토 대상이다.
