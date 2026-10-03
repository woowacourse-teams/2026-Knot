from __future__ import annotations

import re
from uuid import UUID

from notion_api import NotionReader
from notion_models import PageQuery, Record, SearchFilter, parse_notion_id
from settings import AssistantFailure, NotionCoverage, NotionLookup, NotionSource


class ScopedLookup:
    def __init__(self, reader: NotionReader) -> None:
        self.reader = reader
        self.pages: list[tuple[Record, str]] = []
        self.focus_terms: tuple[str, ...] = ()

    async def read_page(self, page_id: UUID) -> None:
        page = await self.reader.metadata("pages", page_id)
        if not await self.reader.in_scope(page):
            raise AssistantFailure("notion_outside_knot")
        lines = [f"{name}: {prop.text()}" for name, prop in page.properties.items() if prop.text()]
        queue = [page_id]
        visited: set[UUID] = set()
        while queue and len(visited) < 30:
            block_id = queue.pop(0)
            if block_id in visited:
                continue
            visited.add(block_id)
            blocks = await self.reader.list_records(f"blocks/{block_id}/children", PageQuery())
            for block in blocks:
                if block.type == "synced_block" and block.body.synced_from is not None:
                    self.reader.warnings.append("외부 동기화 블록은 읽지 않았어.")
                    continue
                if block.type in {"child_page", "child_database", "link_to_page"}:
                    lines.append(f"하위 항목: {block.body.title}")
                    continue
                lines.append("".join(text.plain_text for text in block.body.rich_text))
                if block.body.cells:
                    lines.append(" | ".join("".join(text.plain_text for text in cell) for cell in block.body.cells))
                if block.has_children:
                    queue.append(block.id)
                if block.type in {"unsupported", "file", "pdf", "image", "video", "audio"}:
                    self.reader.warnings.append("첨부 파일·이미지·지원하지 않는 블록 내용은 검색하지 않았어.")
        if queue:
            self.reader.warnings.append("중첩 블록 조회가 페이지당 30개 묶음 한도에 도달했어.")
        self.pages.append((page, "\n".join(line for line in lines if line)))

    async def read_database(self, database_id: UUID) -> None:
        database = await self.reader.metadata("databases", database_id)
        if not await self.reader.in_scope(database):
            raise AssistantFailure("notion_outside_knot")
        if not database.data_sources:
            self.reader.warnings.append("DB의 데이터 소스를 찾지 못했어. 연결 권한을 확인해야 해.")
        for source in database.data_sources:
            rows = await self.reader.list_records(f"data_sources/{source.id}/query", PageQuery())
            for row in rows[:8]:
                if row.object == "page":
                    await self.read_page(row.id)
            if len(rows) > 8:
                self.reader.warnings.append("DB의 처음 8개 문서만 읽었어. 특정 문서는 제목 검색으로 조회해줘.")

    async def search(self, question: str, search_queries: tuple[str, ...] = ()) -> None:
        page_ids = re.findall(r"https://(?:www\.notion\.so|app\.notion\.com|notion\.so)/[^\s?]*?([a-fA-F0-9]{32}|[a-fA-F0-9-]{36})(?:[?\s]|$)", question)
        if page_ids:
            for page_id in dict.fromkeys(page_ids[:3]):
                await self.read_page(parse_notion_id(page_id))
            return
        words = re.findall(r"[A-Za-z가-힣0-9]{2,}", question)
        ignored = {"Knot", "knot", "노션", "알려줘", "알려주세요", "현재", "우리", "뭐지", "무엇인가", "하는", "해줘"}
        terms = list(search_queries) if search_queries else list(dict.fromkeys(word for word in words if word not in ignored))[:5]
        if "프론트엔드" in terms:
            terms.append("FE")
        if "커밋" in terms:
            terms.append("commit")
        if not terms:
            raise AssistantFailure("notion_search_query_needed")
        candidates: dict[UUID, Record] = {}
        expanded: list[str] = []
        for term in list(terms):
            records = await self.reader.list_records("search", PageQuery(query=term, filter=SearchFilter()))
            if not any(term.casefold() in record.page_title().casefold() for record in records):
                parts = re.findall(r"[A-Za-z가-힣0-9]{2,}", term)
                generic = {"타입", "방식", "정책", "규칙", "정보", "개발", "프로젝트", "현재"}
                for part in dict.fromkeys(parts):
                    if part == term or part in generic or part in terms or part in expanded:
                        continue
                    if len(expanded) >= 4:
                        break
                    expanded.append(part)
                    records.extend(await self.reader.list_records("search", PageQuery(query=part, filter=SearchFilter())))
            for record in records:
                if record.object == "page":
                    candidates[record.id] = record
        terms.extend(expanded)
        self.focus_terms = tuple(term for term in terms if term not in {"도메인 규칙", "UseCase", "요구사항", "정책", "FE", "프론트엔드"})
        def score(page: Record) -> int:
            return sum(term.casefold() in page.page_title().casefold() for term in terms)

        ranked = sorted(candidates.values(), key=score, reverse=True)
        if search_queries:
            representatives = [next((page for page in ranked if term.casefold() in page.page_title().casefold()), None) for term in terms]
            preferred = {page.id: page for page in representatives if page is not None}
            ranked = [*preferred.values(), *(page for page in ranked if page.id not in preferred)]
        for page in ranked[:30]:
            try:
                allowed = await self.reader.in_scope(page)
            except AssistantFailure as error:
                if error.code in {"notion_http_403", "notion_http_404"}:
                    self.reader.warnings.append("일부 검색 후보의 상위 페이지 권한을 확인하지 못해 제외했어.")
                    continue
                raise
            if allowed:
                if not search_queries and self.pages and score(self.pages[0][0]) >= 2 and score(page) < score(self.pages[0][0]):
                    break
                await self.read_page(page.id)
            if len(self.pages) >= 5:
                break
        if len(ranked) > 5:
            self.reader.warnings.append("제목 검색 후보 중 Knot 범위와 제목 관련도를 확인한 최대 5개 문서만 읽었어.")

    def result(self) -> NotionLookup:
        remaining = 12000
        sections: list[str] = []
        sources: list[NotionSource] = []
        for page, content in self.pages:
            title = page.page_title()
            if len(content) > 2400 and self.focus_terms:
                lines = content.splitlines()
                hits = {index for index, line in enumerate(lines) if any(term.casefold() in line.casefold() for term in self.focus_terms)}
                windows = {nearby for index in hits for nearby in range(max(0, index - 2), min(len(lines), index + 4))}
                focused = "\n".join(lines[index] for index in sorted(windows))
                content = focused or content
            excerpt = content[:min(2400, remaining)]
            if remaining <= 0:
                self.reader.warnings.append("답변 문맥이 12,000자 한도에 도달했어.")
                break
            if len(excerpt) < len(content):
                self.reader.warnings.append("긴 문서의 일부 내용을 문맥 길이 한도로 잘랐어.")
            sections.append(f"## {title}\n{excerpt}")
            sources.append(NotionSource(title=title, url=f"https://www.notion.so/{page.id.hex}"))
            remaining -= len(excerpt)
        warnings = list(dict.fromkeys(self.reader.warnings))
        warnings.append("Notion 직접 조회는 제목 검색 또는 지정 문서 조회야. 전체 본문 검색·전체 수집을 수행한 결과는 아니야.")
        return NotionLookup(
            context="\n\n".join(sections), sources=sources, warnings=warnings,
            coverage=NotionCoverage(complete=False, documents=len(sources), requests=self.reader.requests, cached=False),
        )
