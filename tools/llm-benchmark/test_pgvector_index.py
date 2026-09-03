from __future__ import annotations

from pathlib import Path

from benchmark_core import Document
from pgvector_index import chunk_documents


def test_chunk_documents_carries_meeting_metadata_into_detail_passages() -> None:
    document = Document(
        Path("meeting.md"),
        "회의",
        "# 폴더구조 컨벤션 회의\n날짜: 2026년 8월 14일\n회의 유형: 프론트 회의\n\n## 논의 내용\n" + "위젯과 피처 규칙 " * 100,
    )

    chunks = chunk_documents((document,), size=120, overlap=20)

    assert len(chunks) > 1
    assert all("날짜: 2026년 8월 14일" in chunk.content for chunk in chunks)
    assert any("위젯과 피처 규칙" in chunk.content for chunk in chunks)
