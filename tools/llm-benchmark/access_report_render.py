"""Markdown rendering for multi-strategy access benchmark reports."""

from __future__ import annotations

from collections.abc import Sequence

from access_report_types import PairSummary, RunMetadataSummary, StrategySummary


def render_report(
    total_records: int,
    retrieval_records: int,
    summaries: Sequence[StrategySummary],
    retrieval_pairs: Sequence[PairSummary],
    e2e_ttft_pairs: Sequence[PairSummary],
    e2e_pairs: Sequence[PairSummary],
    chat_model: str,
    embedding_model: str,
    context_length: int,
    max_tokens: int,
    metadata_summaries: Sequence[RunMetadataSummary] = (),
) -> str:
    """Render the complete report while keeping conclusions conservative."""
    lines = [
        "# Knot LLM 검색 방식 비교 결과",
        "",
        "## 결론",
        "",
        "현재 통제 실험에서는 **Raw를 운영 후보에서 제외**하고, 검색 지연만 보면 `DB 직접 검색`이 가장 짧으며, `RAG`는 Qwen 임베딩 비용을 추가하는 대신 의미 검색 후보를 만든다. `MCP replay`는 실제 Notion MCP가 아니라 로컬 lexical replay이므로 MCP-live의 결론으로 사용하지 않는다.",
        "",
        f"- 검색 관측: `{total_records}`개 기록, 오류 제외 `{retrieval_records}`개",
        "- 답변 생성 관측: 같은 결과 파일의 model latency 기록을 별도 집계",
        f"- 채팅 모델: `{chat_model}`",
        f"- 임베딩 모델: `{embedding_model}`",
        f"- LM Studio 컨텍스트: `{context_length}` tokens, API max_tokens: `{max_tokens}`, 동시성: `1`",
        "- temperature: `0`, Qwen 질의 instruction: 영어 한 문장 사용",
        "",
        "## 전략별 결과",
        "",
        "| 전략 | 검색 기록 | 생성 성공 | 생성 오류 | 검색 p50 | 검색 p95 | 임베딩 p50 | DB/구성 p50 | 5초 내 TTFT | 정답 source hit@5 |",
        "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for summary in summaries:
        lines.append(
            f"| `{summary.strategy}` | {summary.records} | {summary.successful_model_records} | {summary.error_records} | "
            f"{summary.search_p50:.1f}ms | {summary.search_p95:.1f}ms | {summary.embedding_p50:.1f}ms | "
            f"{summary.database_p50:.1f}ms | {summary.e2e_ttft_under_5s}/{summary.e2e_ttft_observations} | "
            f"{summary.source_hit_count}/{summary.source_quality_count} |"
        )
    lines.extend(("", "`source hit@5`는 골드셋에 기록된 page ID가 관련 문서 후보에 포함됐는지만 나타내며, 답변의 의미 정확성을 보장하지 않는다.", ""))
    lines.extend(render_metadata(metadata_summaries))
    lines.extend(render_pairs("## 검색 단계 paired 비교", retrieval_pairs))
    lines.extend(render_pairs("## 답변 첫 표시(end-to-end TTFT) paired 비교", e2e_ttft_pairs))
    lines.extend(render_pairs("## 답변 포함 end-to-end paired 비교", e2e_pairs))
    lines.extend(
        (
            "## Raw의 컨텍스트 한계",
            "",
            "원본 전략은 461개 문서, 약 1,559,218자를 매번 구성했다. 현재 생성 상한 120,000자 보호선을 넘기므로 10개 질문 모두 답변 생성을 시도하지 않고 `context exceeds generation limit`으로 기록했다. 이를 잘라서 성공으로 계산하지 않았다.",
            "",
            "## 해석과 한계",
            "",
            "- 검색 지연 100회 반복은 동일한 10개 질문을 10회 반복한 결과다. paired bootstrap과 순열 p-value는 제공하지만, 독립 질문이 10개뿐이므로 다른 Workspace나 질문 분포로 일반화할 수 없다.",
            "- 답변 생성은 전략별 10개 질문을 1회씩 실행한 최소 10 paired observations다. 생성 모델의 변동과 질문별 컨텍스트 차이를 충분히 추정하는 표본이 아니므로 유의한 승자를 선언하지 않는다.",
            "- 5초 목표는 end-to-end TTFT 기준이어야 한다. 검색만 빠른 DB/RAG가 곧 사용자 답변 성공을 뜻하지 않으며, 관련 문서·답변 의미·충돌·무응답은 사람이 원문과 대조해야 한다.",
            "- 이번 `mcp-replay`는 snapshot을 로컬 함수로 재생했다. 실제 Notion MCP/API의 네트워크·페이지네이션·권한·rate limit은 측정하지 않았다.",
            "",
            "## 관찰된 품질 이슈",
            "",
            "- RAG는 G-001/G-003에서 요구사항 문서의 예시 문장인 ‘팀원들의 PostgreSQL 사용 경험’을 실제 선정 이유처럼 답했다. 기술 스택 원문이 말하는 관계형 데이터 관리와 pgvector 확장 이유와 구분하지 못했으므로, 검색 속도와 별개로 source 유형/예시 판별 또는 reranker가 필요하다.",
            "- G-004의 Redis 결정 이유와 G-006의 문서 위치는 상위 5개 후보에 안정적으로 들어오지 않았다. 현재 Qwen 임베딩을 한국어에 쓸 수 있다는 사실만으로 검색 품질이 충분하다고 결론내릴 수 없다.",
            "- G-009는 DB 규칙과 Java 규칙을 함께 보여야 하는데 일부 전략은 한 규칙만 제공하거나 문서 충돌로 잘못 분류했다.",
            "",
            "## 다음 판정",
            "",
            "MVP 기본 후보는 `마지막 성공 동기화 스냅샷 → DB/키워드 pre-filter → Qwen pgvector RAG → 필요 시 rerank`의 단계형 구조다. 다만 최종 채택 전 30개 이상 독립 질문, 실제 Notion MCP-live, 사람이 검증한 answer/source 품질 라벨을 추가해야 한다.",
            "",
        )
    )
    return "\n".join(lines)


def render_metadata(summaries: Sequence[RunMetadataSummary]) -> list[str]:
    """Render control/live identities without exposing prompt or option values."""
    if not summaries:
        return []
    lines = [
        "## 실행 메타데이터",
        "",
        "통제 실행과 live 실행은 동일 결과로 합치지 않고 phase·run ID별로 표시한다. prompt와 생성 옵션은 값 대신 SHA-256 지문만 표시한다.",
        "",
    ]
    for phase in ("control", "live", "missing"):
        phase_summaries = tuple(item for item in summaries if item.phase == phase)
        if not phase_summaries:
            continue
        title = {"control": "control", "live": "live", "missing": "메타데이터 누락"}[phase]
        lines.extend(
            (
                f"### {title}",
                "",
                "| 입력 | 행 | condition | run ID | snapshot ID | model | prompt SHA-256 | options SHA-256 |",
                "| --- | ---: | --- | --- | --- | --- | --- | --- |",
            )
        )
        for item in phase_summaries:
            lines.append(
                f"| {item.input_label} | {item.rows} | {item.condition or '—'} | {item.run_id or '—'} | {item.snapshot_id or '—'} | {item.model or '—'} | {item.prompt_sha256 or '—'} | {item.options_sha256 or '—'} |"
            )
        lines.append("")
    lines.extend(
        (
            "`--require-run-metadata`를 사용하는 최종 평가에서는 metadata 누락, 서로 다른 run ID/phase/condition/snapshot/model/prompt/options 혼합을 실패로 처리한다.",
            "",
        )
    )
    return lines


def render_pairs(title: str, pairs: Sequence[PairSummary]) -> list[str]:
    """Render paired deltas and uncertainty intervals."""
    lines = [title, "", "| A | B | metric | pairs | median Δ (B−A) | 95% bootstrap CI | B faster | permutation p |", "| --- | --- | --- | ---: | ---: | --- | ---: | ---: |"]
    for pair in pairs:
        lines.append(
            f"| `{pair.strategy_a}` | `{pair.strategy_b}` | `{pair.metric}` | {pair.pairs} | {pair.median_delta:.1f}ms | "
            f"[{pair.ci_low:.1f}, {pair.ci_high:.1f}]ms | {pair.b_faster}/{pair.pairs} | {pair.p_value:.5f} |"
        )
    lines.extend(("", "Δ가 음수면 B가 더 빠르다. p-value는 효과 크기나 품질을 보정하지 않으며, 독립 질문 수가 적은 현재 결과로 일반화 승자를 선언하지 않는다.", ""))
    return lines
