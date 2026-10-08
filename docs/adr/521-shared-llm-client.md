# 공통 통신은 global/infrastructure/llm, 문서 프롬프트·해석은 document/infrastructure/llm에 둔다.

## 상태

Proposed

## 관련 Issue

- #521 [BE] 공통 LLM API 연동과 전사 원문 주제 분류 구현

## 한 줄 요약

공통 통신은 global/infrastructure/llm, 문서 프롬프트·해석은 document/infrastructure/llm에 둔다.

## 왜 이 결정이 필요했나

문서 분류·작성에서 공통으로 사용할 단일 LM Studio API 통신이 필요하다.

사용자가 llm 별도 패키지와 global/infrastructure/llm 및 document/infrastructure/llm의 분리를 논의하고 후자를 선택했다.

결정 동인:

- 공통 통신 재사용
- 문서 의미 해석 책임 분리
- 단일 공급자 구현의 단순성

## 트레이드 오프

- 독립 llm 도메인 패키지: 사용자 제안. 현재는 별도 도메인 규칙보다 외부 통신 책임이 중심이다.
- global 공통 client + document 전용 프롬프트·해석: 사용자 선택. 문서 규칙을 document에 보존한다.

## 무엇을 결정했나

공통 통신은 global/infrastructure/llm, 문서 프롬프트·해석은 document/infrastructure/llm에 둔다.

문서 의미를 공통 client에 섞지 않으면서 단일 공급자의 단순한 구현을 유지한다.

## 결과

- #522가 같은 통신 client를 재사용한다.
- 사용자 공급자 교체 요구를 반영해 `LlmClient.complete` 전략 인터페이스를 둔다. 문서 classifier는 인터페이스에만 의존하고 `LlmConfig`가 첫 `LmStudioClient` 전략을 주입한다.
- 지원하지 않는 공급자 설정은 시작 시 실패한다. 두 번째 공급자의 실제 계약이 정해질 때 전략을 추가하며 Factory나 미구현 공급자 클래스를 먼저 만들지 않는다.
- 상위 서비스가 classify를 DB 트랜잭션 밖에서 호출한다.

## 다시 논의해야 할 조건

- 두 번째 공급자를 실제 지원할 때
- 공통 LLM 비즈니스 도메인 규칙이 생길 때

## 확인

- 예정 경로: `docs/adr/521-shared-llm-client.md`
- 결정 주체: 사용자와 구현 담당자; 팀 승인은 아직 확인하지 않음
- AI 하네스가 Proposed ADR 파일을 생성했다.
- 팀이 PR에서 승인한 뒤 Accepted로 바꾼다.
