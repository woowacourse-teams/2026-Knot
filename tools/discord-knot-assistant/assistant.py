from __future__ import annotations

import anyio
import discord
import httpx2
import socket
import structlog
from time import perf_counter
from pydantic import ValidationError

from codex_cli import CodexRequest, codex_answer, make_prompt, response_chunks, visible_answer
from failure_details import describe_failure
from meeting_commands import MeetingCommands, sync_meeting_commands
from meeting_published import restore_meeting_views
from meeting_repository import MeetingRepository
from meeting_views import render_session
from notion_mcp_client import lookup_via_mcp
from question_classifier import classify_question
from settings import AssistantFailure, NotionLookup, Settings
from sync_guard import claim_sync, finish_sync


class KnotAssistant(discord.Client):
    def __init__(self, settings: Settings) -> None:
        intents = discord.Intents.default()
        intents.message_content = True
        super().__init__(intents=intents)
        self.settings = settings
        self.notion_http: httpx2.AsyncClient | None = None
        self.model_limiter: anyio.CapacityLimiter | None = None
        self.notion_lock: anyio.Lock | None = None
        self.log = structlog.get_logger("knot_assistant")
        self.command_tree = discord.app_commands.CommandTree(self)
        self.meeting_repository = MeetingRepository()
        self.command_tree.add_command(MeetingCommands(settings, self.meeting_repository), guild=discord.Object(id=settings.discord_guild_id))

    async def setup_hook(self) -> None:
        limits = httpx2.Limits(max_connections=200, max_keepalive_connections=40, keepalive_expiry=30.0)
        timeout = httpx2.Timeout(connect=5.0, read=300.0, write=10.0, pool=10.0)
        transport = httpx2.AsyncHTTPTransport(
            http2=True,
            retries=3,
            limits=limits,
            socket_options=[(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)],
        )
        self.notion_http = httpx2.AsyncClient(
            transport=transport,
            timeout=timeout,
            follow_redirects=False,
        )
        self.model_limiter = anyio.CapacityLimiter(2)
        self.notion_lock = anyio.Lock()
        if self.application_id is not None:
            await sync_meeting_commands(self.command_tree, self.settings.discord_guild_id)
            await restore_meeting_views(self, self.settings, self.meeting_repository, render_session)

    async def close(self) -> None:
        if self.notion_http is not None:
            await self.notion_http.aclose()
        await super().close()

    async def on_ready(self) -> None:
        if self.user is not None:
            self.log.info("discord.gateway.ready", bot_user_id=self.user.id)

    async def on_message(self, message: discord.Message) -> None:
        if message.author.bot or message.guild is None or message.guild.id != self.settings.discord_guild_id:
            return
        if self.user is None or self.user not in message.mentions:
            return

        question = message.content.replace(f"<@{self.user.id}>", "")
        question = question.replace(f"<@!{self.user.id}>", "").strip()
        started = perf_counter()
        stage = "질문 분류"
        try:
            async with message.channel.typing():
                if self.model_limiter is None:
                    raise AssistantFailure("bot_not_ready")
                classification = await anyio.to_thread.run_sync(
                    classify_question, question, limiter=self.model_limiter
                )
                route = classification.route
                classified = perf_counter()
                self.log.info(
                    "assistant.request.routed",
                    message_id=message.id,
                    reasoning_effort=route.effort.value,
                    retrieve_notion=route.retrieve_notion,
                    classifier=classification.source,
                    failure_code=classification.failure_code,
                    search_queries=classification.search_queries,
                    classification_seconds=round(classified - started, 3),
                )
                lookup = NotionLookup()
                if route.retrieve_notion:
                    stage = "Notion 문서 조회"
                    lookup = await self.retrieve_notion(question, classification.search_queries)
                notion_finished = perf_counter()
                prompt = make_prompt(question, lookup, self.settings.max_context_chars, classification.intent)
                stage = "Codex 답변 생성"
                answer = await anyio.to_thread.run_sync(
                    codex_answer,
                    prompt,
                    CodexRequest(model=self.settings.codex_model, effort=route.effort),
                    limiter=self.model_limiter,
                )
                codex_finished = perf_counter()
        except (AssistantFailure, httpx2.HTTPError, ValidationError) as error:
            failure = describe_failure(error, stage)
            self.log.error(
                "assistant.request.failed",
                message_id=message.id,
                stage=stage,
                failure_code=failure.code,
                error_type=type(error).__name__,
            )
            await self.send_reply(message, failure.message)

            return

        for chunk in response_chunks(visible_answer(answer, lookup)):
            if not await self.send_reply(message, chunk):
                return

        self.log.info(
            "assistant.request.completed",
            message_id=message.id,
            reasoning_effort=route.effort.value,
            classifier=classification.source,
            classification_seconds=round(classified - started, 3),
            notion_seconds=round(notion_finished - classified, 3),
            codex_wait_seconds=round(codex_finished - notion_finished, 3),
            total_seconds=round(perf_counter() - started, 3),
        )

    async def send_reply(self, message: discord.Message, content: str) -> bool:
        try:
            await message.reply(content, mention_author=False, allowed_mentions=discord.AllowedMentions.none())
        except discord.HTTPException as error:
            self.log.error(
                "assistant.request.failed", message_id=message.id, stage="Discord 답변 전송",
                failure_code=f"discord_http_{error.status}", error_type=type(error).__name__,
            )
            return False
        return True

    async def retrieve_notion(self, question: str, search_queries: tuple[str, ...] = ()) -> NotionLookup:
        try:
            with anyio.fail_after(20):
                lookup = await self.retrieve_n8n(" ".join(search_queries) if search_queries else question)
            relevant = not search_queries or any(term.casefold() in lookup.context.casefold() for term in search_queries)
            if lookup.sources and lookup.context.strip() and relevant:
                return lookup
            reason = "n8n 검색 결과에 답변 근거가 없어"
        except TimeoutError:
            reason = "n8n 문서 조회가 20초 안에 완료되지 않았어"
            self.log.warning("notion.lookup.fallback", failure_code="n8n_lookup_timeout", provider="notion_mcp")
        except (AssistantFailure, httpx2.HTTPError, ValidationError) as error:
            failure = describe_failure(error, "n8n 문서 조회")
            reason = failure.message
            self.log.warning("notion.lookup.fallback", failure_code=failure.code, provider="notion_mcp")
        try:
            if self.notion_lock is None:
                raise AssistantFailure("n8n_client_not_ready")
            async with self.notion_lock:
                lookup = await lookup_via_mcp(question, search_queries)
        except (AssistantFailure, httpx2.HTTPError, ValidationError) as error:
            failure = describe_failure(error, "Notion MCP 직접 조회")
            raise AssistantFailure("notion_both_routes_failed", f"{reason}\n{failure.message}") from error
        return lookup

    async def retrieve_n8n(self, question: str) -> NotionLookup:
        if self.notion_http is None or self.notion_lock is None:
            raise AssistantFailure("n8n_client_not_ready")
        headers = {"X-Knot-Assistant-Secret": self.settings.n8n_webhook_secret.get_secret_value()}
        async with self.notion_lock:
            start_sync = False
            for _ in range(1200):
                response = await self.notion_http.post(
                    str(self.settings.n8n_notion_webhook_url),
                    headers=headers,
                    json={
                        "query": question,
                        "root_page_id": self.settings.notion_root_page_id,
                        "guild_id": str(self.settings.discord_guild_id),
                        "action": "ask" if start_sync else "status",
                    },
                    timeout=httpx2.Timeout(connect=5, read=15, write=10, pool=10),
                )
                response.raise_for_status()
                lookup = NotionLookup.model_validate_json(response.content)
                if not start_sync and (lookup.pending or lookup.refreshing) and claim_sync():
                    start_sync = True
                    continue
                if not lookup.pending:
                    if not lookup.refreshing:
                        finish_sync()
                    return lookup
                start_sync = False
                await anyio.sleep(3)
        raise AssistantFailure("notion_sync_timeout")
