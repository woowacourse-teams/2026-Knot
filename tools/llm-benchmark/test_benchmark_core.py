# /// script
# requires-python = ">=3.14"
# dependencies = ["pytest"]
# ///

# ─── How to run ───
# 1. Install uv (if not installed):
#      curl -LsSf https://astral.sh/uv/install.sh | sh
# 2. Run directly (no venv, no pip install needed):
#      uv run --with pytest pytest tools/llm-benchmark/test_benchmark_core.py
# ──────────────────

from __future__ import annotations

from pathlib import Path

from benchmark_core import Strategy, build_context, load_snapshot
from gold_set import load_cases


def test_load_snapshot_reads_supported_files_and_skips_hidden_files(tmp_path: Path) -> None:
    (tmp_path / "decision.md").write_text("# PostgreSQL\nUse PostgreSQL.", encoding="utf-8")
    (tmp_path / "notes.txt").write_text("Redis session store", encoding="utf-8")
    (tmp_path / ".private.md").write_text("must not load", encoding="utf-8")
    (tmp_path / "Key & 비밀번호").mkdir()
    (tmp_path / "Key & 비밀번호" / "credentials.md").write_text("do not send", encoding="utf-8")
    (tmp_path / "private-key.md").write_text(
        "-----BEGIN PRIVATE KEY-----\nnot a real key\n-----END PRIVATE KEY-----",
        encoding="utf-8",
    )
    (tmp_path / "config.md").write_text("password: should-not-leave-the-loader", encoding="utf-8")
    (tmp_path / "table.csv").write_text("name,value\nalpha,1\n", encoding="utf-8")
    (tmp_path / "table_all.csv").write_text("value,name\n1,alpha\n", encoding="utf-8")

    documents = load_snapshot(tmp_path)

    assert [document.path.name for document in documents] == ["decision.md", "notes.txt", "table.csv"]
    assert documents[0].title == "PostgreSQL"


def test_load_cases_reads_single_and_follow_up_questions(tmp_path: Path) -> None:
    gold_set = tmp_path / "gold.md"
    gold_set.write_text(
        """# Gold set

### G-001 — 사실
- 상태: `confirmed`
- 유형: `fact`
- 질문: `DB 뭐 써?`
- 기대 답변:

  > PostgreSQL
- page ID: `page-1`

### G-002 — 후속
- 상태: `confirmed`
- 유형: `follow_up`
- 대화:

  ```text
  사용자: DB 뭐 써?
  AI: PostgreSQL입니다.
  사용자: 왜?
  ```
- page ID: `page-1`
""",
        encoding="utf-8",
    )

    cases = load_cases(gold_set)

    assert cases[0].case_id == "G-001"
    assert cases[0].turns == ("DB 뭐 써?",)
    assert cases[0].source_ids == ("page-1",)
    assert cases[0].category == "fact"
    assert cases[1].turns == ("DB 뭐 써?", "왜?")


def test_load_cases_reads_unquoted_and_multiple_page_ids(tmp_path: Path) -> None:
    gold_set = tmp_path / "gold.md"
    gold_set.write_text(
        """### G-001
- 질문: `규칙 뭐야?`
- page ID:
  - `page-1`
  - `page-2`
""",
        encoding="utf-8",
    )

    cases = load_cases(gold_set)

    assert cases[0].source_ids == ("page-1", "page-2")


def test_build_context_retrieves_relevant_chunks_and_marks_mcp_call(tmp_path: Path) -> None:
    (tmp_path / "db.md").write_text("PostgreSQL 관계형 데이터베이스\n" + ("배경 문장 " * 400), encoding="utf-8")
    (tmp_path / "redis.md").write_text("Redis 중앙 세션 저장소", encoding="utf-8")
    documents = load_snapshot(tmp_path)

    rag_context = build_context(Strategy.RAG, documents, "PostgreSQL 뭐 써?", top_k=1)
    mcp_context = build_context(Strategy.MCP_REPLAY, documents, "Redis 세션", top_k=1)

    assert rag_context.retrieved_count == 1
    assert "PostgreSQL" in rag_context.text
    assert "Redis" not in rag_context.text
    assert len(set(rag_context.source_paths)) == 1
    assert mcp_context.tool_calls == 1
    assert "Redis" in mcp_context.text
