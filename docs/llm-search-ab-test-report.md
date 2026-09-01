# Knot LLM 검색 방식 비교 결과

## 결론

현재 통제 실험에서는 **Raw를 운영 후보에서 제외**하고, 검색 지연만 보면 `DB 직접 검색`이 가장 짧으며, `RAG`는 Qwen 임베딩 비용을 추가하는 대신 의미 검색 후보를 만든다. `MCP replay`는 실제 Notion MCP가 아니라 로컬 lexical replay이므로 MCP-live의 결론으로 사용하지 않는다. 2026-09-01에는 LM Studio에서 실제 Notion MCP를 호출하는 smoke test까지 완료했지만, Java가 Workspace별 credential을 전달하는 경로와 전체 품질·지연 비교는 아직 보류한다.

- 검색 관측: `400`개 기록, 오류 제외 `400`개
- 답변 생성 관측: 같은 결과 파일의 model latency 기록을 별도 집계
- 채팅 모델: `qwen/qwen3.6-27b`
- 임베딩 모델: `text-embedding-qwen3-embedding-0.6b:2`
- LM Studio 컨텍스트: `32768` tokens, API max_tokens: `4096`, 동시성: `1`
- temperature: `0`, Qwen 질의 instruction: 영어 한 문장 사용
- 실제 MCP smoke test: LM Studio `mcp/notion` + Notion OAuth, `notion-search`/`notion-fetch`

## 전략별 결과

| 전략 | 검색 기록 | 생성 성공 | 생성 오류 | 검색 p50 | 검색 p95 | 임베딩 p50 | DB/구성 p50 | 5초 내 TTFT | 정답 source hit@5 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `raw` | 100 | 0 | 10 | 0.6ms | 1.5ms | 0.0ms | 0.6ms | 0/0 | 90/90 |
| `rag` | 100 | 10 | 0 | 200.2ms | 271.5ms | 132.4ms | 73.4ms | 10/10 | 60/90 |
| `db` | 100 | 10 | 0 | 79.6ms | 119.2ms | 0.0ms | 79.6ms | 10/10 | 40/90 |
| `mcp-replay` | 100 | 10 | 0 | 118.7ms | 129.7ms | 0.0ms | 118.7ms | 10/10 | 50/90 |

`source hit@5`는 골드셋에 기록된 page ID가 관련 문서 후보에 포함됐는지만 나타내며, 답변의 의미 정확성을 보장하지 않는다.

## 검색 단계 paired 비교

| A | B | metric | pairs | median Δ (B−A) | 95% bootstrap CI | B faster | permutation p |
| --- | --- | --- | ---: | ---: | --- | ---: | ---: |
| `rag` | `db` | `search_ms` | 100 | -108.5ms | [-134.2, -96.0]ms | 100/100 | 0.00010 |
| `rag` | `mcp-replay` | `search_ms` | 100 | -78.5ms | [-99.6, -63.0]ms | 100/100 | 0.00010 |
| `db` | `mcp-replay` | `search_ms` | 100 | 39.1ms | [31.7, 44.3]ms | 4/100 | 0.00010 |

Δ가 음수면 B가 더 빠르다. p-value는 효과 크기나 품질을 보정하지 않으며, 독립 질문 수가 적은 현재 결과로 일반화 승자를 선언하지 않는다.

## 답변 첫 표시(end-to-end TTFT) paired 비교

| A | B | metric | pairs | median Δ (B−A) | 95% bootstrap CI | B faster | permutation p |
| --- | --- | --- | ---: | ---: | --- | ---: | ---: |
| `rag` | `db` | `ttft_ms` | 10 | -85.7ms | [-151.1, 7.7]ms | 7/10 | 0.17368 |
| `rag` | `mcp-replay` | `ttft_ms` | 10 | 692.6ms | [548.1, 770.7]ms | 0/10 | 0.00170 |
| `db` | `mcp-replay` | `ttft_ms` | 10 | 747.7ms | [651.4, 832.3]ms | 0/10 | 0.00280 |

Δ가 음수면 B가 더 빠르다. p-value는 효과 크기나 품질을 보정하지 않으며, 독립 질문 수가 적은 현재 결과로 일반화 승자를 선언하지 않는다.

## 답변 포함 end-to-end paired 비교

| A | B | metric | pairs | median Δ (B−A) | 95% bootstrap CI | B faster | permutation p |
| --- | --- | --- | ---: | ---: | --- | ---: | ---: |
| `rag` | `db` | `total_ms` | 10 | -1847.3ms | [-6346.2, 1595.1]ms | 7/10 | 0.19108 |
| `rag` | `mcp-replay` | `total_ms` | 10 | -1428.9ms | [-6748.3, 1442.3]ms | 7/10 | 0.29337 |
| `db` | `mcp-replay` | `total_ms` | 10 | 753.6ms | [202.8, 2039.9]ms | 2/10 | 0.52205 |

Δ가 음수면 B가 더 빠르다. p-value는 효과 크기나 품질을 보정하지 않으며, 독립 질문 수가 적은 현재 결과로 일반화 승자를 선언하지 않는다.

## 실제 LM Studio/Notion MCP-live smoke test

2026-09-01에 LM Studio `0.4.16`의 native `/api/v1/chat`으로 `qwen/qwen3.6-27b`를 실행했다. `mcp.json`에 등록한 Notion 서버를 `mcp/notion` plugin으로 활성화했고, LM Studio 출력의 `provider_info.plugin_id`가 `mcp/notion`인지 확인했다. 따라서 아래 결과는 Codex Notion connector나 로컬 MCP replay가 아니라 LM Studio가 실제 Notion MCP의 `notion-search`와 `notion-fetch`를 호출한 결과다.

토큰 값은 기록하지 않았다. 이번 검증은 LM Studio가 관리하는 Notion OAuth 연결을 사용했으며, Java가 Workspace별 MCP access token을 주입하는 운영 경로의 검증은 아니다.

| 질의 | 실제 도구 호출 | 결과 | 관측 |
| --- | --- | --- | --- |
| PostgreSQL을 왜 사용하기로 했는가 | `notion-search` → `notion-fetch` | 통과 | 기술 스택 문서에서 관계형 데이터 안정적 관리와 향후 `pgvector` 확장을 근거로 답변 |
| `그렇게 결정한 이유는 뭐야?` | `notion-search` → `notion-fetch` → `notion-search` | 문맥·근거 통과, 속도 실패 | `189,976.6ms`, model TTFT `4.441s`, reasoning output `2,565` tokens |
| 로드맵 회의는 언제 했는가 | `notion-search` → `notion-fetch` × 2 | 내용 통과, 속도 실패 | `38,310.4ms`, model TTFT `3.081s` |
| Redis 세션 저장소 결정이 최종인가 | `notion-search` × 5, `notion-fetch` × 3 | 예시 문단과 공식 결정 부재를 구분 | `76,742.0ms`, model TTFT `1.403s` |
| 넓은 코드 컨벤션 질문 | `notion-search` → `notion-fetch` × 3 | 실패 | `60,782.1ms`, 백엔드 문서를 가져와 폴더·파일명 질문에 답하지 못함 |
| 구체적인 폴더·파일명 규칙 질문 | `notion-search` → `notion-fetch` × 2 | 통과 | `41,921.1ms`, model TTFT `2.719s`, `camelCase`/`PascalCase` 규칙 확인 |

첫 PostgreSQL 질의의 최종 답변은 다음 근거와 일치했다.

> 문서·워크스페이스·사용자·동기화 상태와 같은 관계형 데이터를 안정적으로 관리하기 위해 PostgreSQL을 사용하고, 향후 `pgvector`를 활용한 문서 임베딩 및 벡터 검색으로 확장할 수 있다는 점을 고려했다.

관련 문서는 `01. 기술 스택과 라이브러리 도입`이다. 후속 질문은 `previous_response_id`로 앞선 PostgreSQL 문맥을 유지했지만, 추가 검색과 기본 reasoning 때문에 5초 목표를 크게 초과했다. 넓은 코드 컨벤션 질의가 실패하고 검색어를 구체화한 뒤 통과한 결과는 MCP tool 자체뿐 아니라 질의 재작성·검색 결과 제한·문서 유형 판별이 필요하다는 것을 보여준다.

이 smoke test는 연결·도구 호출·기본 근거 생성이 가능한지를 확인한 결과다. 5초 목표와 전체 Workspace 일반화의 통계적 판정으로 사용하지 않으며, Java credential forwarding, Workspace 격리, 30개 이상 독립 질문의 사람 품질 라벨을 추가로 검증해야 한다.

## Raw의 컨텍스트 한계
원본 전략은 461개 문서, 약 1,559,218자를 매번 구성했다. 현재 생성 상한 120,000자 보호선을 넘기므로 10개 질문 모두 답변 생성을 시도하지 않고 `context exceeds generation limit`으로 기록했다. 이를 잘라서 성공으로 계산하지 않았다.

## 해석과 한계

- 검색 지연 100회 반복은 동일한 10개 질문을 10회 반복한 결과다. paired bootstrap과 순열 p-value는 제공하지만, 독립 질문이 10개뿐이므로 다른 Workspace나 질문 분포로 일반화할 수 없다.
- 답변 생성은 전략별 10개 질문을 1회씩 실행한 최소 10 paired observations다. 생성 모델의 변동과 질문별 컨텍스트 차이를 충분히 추정하는 표본이 아니므로 유의한 승자를 선언하지 않는다.
- 5초 목표는 end-to-end TTFT 기준이어야 한다. 검색만 빠른 DB/RAG가 곧 사용자 답변 성공을 뜻하지 않으며, 관련 문서·답변 의미·충돌·무응답은 사람이 원문과 대조해야 한다.
- 이번 `mcp-replay`는 snapshot을 로컬 함수로 재생했다. 실제 Notion MCP/API의 네트워크·페이지네이션·권한·rate limit은 측정하지 않았다.

## 관찰된 품질 이슈

- RAG는 G-001/G-003에서 요구사항 문서의 예시 문장인 ‘팀원들의 PostgreSQL 사용 경험’을 실제 선정 이유처럼 답했다. 기술 스택 원문이 말하는 관계형 데이터 관리와 pgvector 확장 이유와 구분하지 못했으므로, 검색 속도와 별개로 source 유형/예시 판별 또는 reranker가 필요하다.
- G-004의 Redis 결정 이유와 G-006의 문서 위치는 상위 5개 후보에 안정적으로 들어오지 않았다. 현재 Qwen 임베딩을 한국어에 쓸 수 있다는 사실만으로 검색 품질이 충분하다고 결론내릴 수 없다.
- G-009는 DB 규칙과 Java 규칙을 함께 보여야 하는데 일부 전략은 한 규칙만 제공하거나 문서 충돌로 잘못 분류했다.

## 다음 판정

MVP 기본 경로는 `마지막 성공 동기화 스냅샷 → DB/키워드 pre-filter → Qwen pgvector RAG → 필요 시 rerank`의 단계형 구조로 결정했다. 실제 Notion MCP 연결은 가능하다는 것을 확인했지만, 현재 live smoke test는 end-to-end 5초 목표를 만족하지 못했고 LM Studio-managed OAuth만 검증했다. 전체 사용자 대상 운영 전환 전에는 Java credential forwarding, 30개 이상 독립 질문, 사람이 검증한 answer/source 품질 라벨을 추가해야 한다.
