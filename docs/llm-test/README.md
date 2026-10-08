# LLM API 품질·속도 실험

관련 이슈: #521 공통 LLM API 연동·주제 분류, #522 주제별 Markdown 생성.

현재 구현 브랜치: `be/feature/#521`. 아래 초기 작성·선별 기록은 구현 전 실험이고, #521 분류 관찰은 별도 문서로 구분한다.

## 현재 추천 후보

일반 작성은 **작성 프롬프트 v7 + 추론 512**, temperature=0.2, top_p=0.9, top_k=20, repeat_penalty=1.0, max_tokens=4096을 추천 후보로 선택했다. 서버 컨텍스트는 32768을 유지한다.

긴 합성 원문의 빠른 경로는 **발언 선별 v3 → 작성 v7**, 두 단계 모두 추론 끔이다. 같은 장문 한 주제에서 선별부터 문서 완성까지 3회 통과했고 29.26~37.80초였다. 여러 실제 장문에 검증된 운영 기본값으로 확대하지 않는다.

- [현재 추천값·요청에 넣을 설정](recommended-settings.md)
- [#521 전체 원문 주제 분류·합성 실제 API 10회](521-topic-classification-2026-10-08.md)
- [추가 HTTP 54회 비교·실제 출력·판정 근거](settings-comparison-2026-10-08.md)
- [작성용 시스템 프롬프트 v7 전체](topic-writer-v7.md)
- [발언 선별 시스템 프롬프트 v3 전체](topic-evidence-selector-v3.md)
- [장문 합성 입력 v2 전체](synthetic-long-v2.md)

## 이전 비교와 버전

- [초기 24회와 v4 8회·전체 프롬프트·실제 출력](writing-quality-2026-10-08.md)
- [SDK 추론 예산 8회와 HTTP 확인 1회](budget-tuning-2026-10-08.md)
- 작성 후보: [v4](topic-writer-v4.md), [v5](topic-writer-v5.md), [v6](topic-writer-v6.md).

## 기록 원칙과 제한

실제 서버 응답, 합성 입력, 설정, 판정 근거를 Markdown으로 남긴다. 인증값·실행 스크립트·개인 회의 원문·추론 내용은 포함하지 않는다. JSON 해석 성공과 문서 의미 정확성을 별도로 판정하고 실패한 결과도 보존한다.

모델 정보 API의 추론 지원 옵션은 off/on이다. low/medium/high를 실제 강도 단계라고 판단하지 않는다. top_p·top_k·repeat_penalty의 전체 조합이나 동시 처리량은 비교하지 않았다. 현재 자료는 구현 전 실험이며 #521·#522 구현 완료 근거로 사용하지 않는다.
