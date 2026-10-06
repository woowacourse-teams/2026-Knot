# /// script
# requires-python = ">=3.12"
# dependencies = [
#   "anyio>=4,<5",
#   "discord.py>=2.6,<3",
#   "httpx2[http2,brotli,zstd]",
#   "keyring>=25,<27",
#   "mcp>=1.12,<2",
#   "pydantic>=2.11,<3",
#   "pydantic-settings>=2.8,<3",
#   "structlog>=25,<26",
#   "typer>=0.15,<1",
# ]
# ///
# ─── How to run ───
#   uv run --script tools/discord-knot-assistant/bot.py --help

from __future__ import annotations

import getpass
import anyio
import json
import logging
import os
import sys
import shutil
import subprocess
from pathlib import Path
from typing import Final
from time import perf_counter

import keyring
import structlog
import typer
from keyring.backends.macOS import Keyring

from assistant import KnotAssistant
from codex_cli import CodexRequest, codex_answer, make_prompt, visible_answer
from failure_details import describe_failure
from macos_service import install_launch_agent, uninstall_launch_agent
from question_classifier import classify_question
from settings import AssistantFailure, KEYCHAIN_SERVICE, NotionLookup, Settings
import httpx2
from pydantic import ValidationError

SCRIPT_DIR: Final = Path(__file__).resolve().parent
app = typer.Typer(no_args_is_help=True, pretty_exceptions_show_locals=False)


def configure_logging() -> None:
    renderer: structlog.types.Processor = (
        structlog.processors.JSONRenderer()
        if os.environ.get("KNOT_ASSISTANT_SERVICE") == "1"
        else structlog.dev.ConsoleRenderer()
    )
    structlog.configure(
        processors=[
            structlog.processors.TimeStamper(fmt="iso"),
            structlog.processors.add_log_level,
            structlog.processors.format_exc_info,
            renderer,
        ],
        logger_factory=structlog.PrintLoggerFactory(file=sys.stderr),
        wrapper_class=structlog.make_filtering_bound_logger(logging.INFO),
    )


def set_keychain_token(token: str) -> None:
    keyring.set_keyring(Keyring())
    keyring.set_password(KEYCHAIN_SERVICE, getpass.getuser(), token)


def get_keychain_token() -> str | None:
    keyring.set_keyring(Keyring())
    return keyring.get_password(KEYCHAIN_SERVICE, getpass.getuser())


@app.command()
def configure() -> None:
    webhook_url = typer.prompt("n8n Production Webhook URL").strip()
    if not webhook_url.startswith(
        ("https://n8n.aitestbed.kr/webhook/", "https://n8n.aitestbed.kr:5678/webhook/")
    ):
        raise typer.BadParameter("Knot n8n의 HTTPS Production Webhook URL을 입력해줘.")
    webhook_secret = typer.prompt(
        "n8n Header Auth Secret",
        hide_input=True,
        confirmation_prompt=True,
    )
    token = typer.prompt("Discord Bot Token", hide_input=True, confirmation_prompt=True)
    config_path = SCRIPT_DIR / ".env"
    descriptor = os.open(config_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(descriptor, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as config_file:
        config_file.write(f"N8N_NOTION_WEBHOOK_URL={json.dumps(webhook_url)}\n")
        config_file.write(f"N8N_WEBHOOK_SECRET={json.dumps(webhook_secret)}\n")
    Settings()
    set_keychain_token(token)
    typer.echo("n8n 주소와 인증 비밀값을 설정했어. Discord 토큰은 macOS Keychain에 저장했어.")


@app.command("set-n8n-auth")
def set_n8n_auth() -> None:
    webhook_url = typer.prompt("n8n Production Webhook URL").strip()
    if not webhook_url.startswith(
        ("https://n8n.aitestbed.kr/webhook/", "https://n8n.aitestbed.kr:5678/webhook/")
    ):
        raise typer.BadParameter("Knot n8n의 HTTPS Production Webhook URL을 입력해줘.")
    webhook_secret = typer.prompt(
        "n8n Header Auth Secret",
        hide_input=True,
        confirmation_prompt=True,
    )
    config_path = SCRIPT_DIR / ".env"
    descriptor = os.open(config_path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(descriptor, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as config_file:
        config_file.write(f"N8N_NOTION_WEBHOOK_URL={json.dumps(webhook_url)}\n")
        config_file.write(f"N8N_WEBHOOK_SECRET={json.dumps(webhook_secret)}\n")
    Settings()
    typer.echo("n8n 주소와 인증 비밀값을 로컬 .env에 저장했어. Discord 토큰은 건드리지 않았어.")


@app.command("set-token")
def set_token() -> None:
    token = typer.prompt("Discord Bot Token", hide_input=True, confirmation_prompt=True)
    set_keychain_token(token)
    typer.echo("Discord 봇 토큰을 macOS Keychain에 저장했어.")


@app.command()
def run() -> None:
    configure_logging()
    settings = Settings()
    token = get_keychain_token()
    if token is None:
        raise typer.BadParameter("먼저 `uv run --script bot.py set-token`으로 Discord 토큰을 저장해줘.")
    KnotAssistant(settings).run(token, log_handler=None)


@app.command("set-notion-token")
def set_notion_token() -> None:
    token = typer.prompt("Notion Access Token", hide_input=True, confirmation_prompt=True).strip()
    if not token.startswith(("ntn_", "secret_")):
        raise typer.BadParameter("Notion 통합 액세스 토큰을 입력해줘.")
    uv = shutil.which("uv")
    if uv is None:
        raise typer.BadParameter("uv 실행 파일을 찾지 못했어.")
    saved = subprocess.run(
        [uv, "run", "--python", "3.11", "--script", str(SCRIPT_DIR / "notion_credentials.py")],
        input=token, text=True, capture_output=True, check=False,
    )
    if saved.returncode != 0:
        typer.echo("Notion 키 저장 단계에서 실패했어. Mac 키체인 접근 상태를 확인해줘.")
        raise typer.Exit(1)
    typer.echo("Notion 액세스 키를 macOS Keychain에 저장했어.")


async def verify_flow(question: str) -> None:
    settings = Settings()
    started = perf_counter()
    async with KnotAssistant(settings) as client:
        await client.setup_hook()
        classification = await anyio.to_thread.run_sync(classify_question, question)
        route = classification.route
        classified = perf_counter()
        typer.echo(f"질문 분류: {classification.source}, {classified - started:.2f}s")
        typer.echo(f"질문 의도: {classification.intent}")
        typer.echo(f"검색어: {', '.join(classification.search_queries)}")
        if classification.failure_code is not None:
            typer.echo(f"분류 fallback: {classification.failure_code}")
        lookup = await client.retrieve_notion(question, classification.search_queries) if route.retrieve_notion else NotionLookup()
        notion_finished = perf_counter()
        typer.echo(f"Codex 모델: {settings.codex_model}, reasoning: {route.effort.value}")
        typer.echo(f"Notion 조회: {'사용' if route.retrieve_notion else '생략'}")
        typer.echo(f"Notion 문맥: {len(lookup.context)}자, 출처: {len(lookup.sources)}개")
        if lookup.coverage is not None:
            typer.echo(f"조회 범위: {lookup.coverage.model_dump_json()}")
        for warning in lookup.warnings:
            typer.echo(f"조회 경고: {warning}")
        for notice in lookup.notices:
            typer.echo(f"경로 전환: {notice}")
        prompt = make_prompt(question, lookup, settings.max_context_chars, classification.intent)
        answer = await anyio.to_thread.run_sync(
            codex_answer, prompt, CodexRequest(model=settings.codex_model, effort=route.effort)
        )
        typer.echo(
            f"처리 시간: Notion {notion_finished - classified:.2f}s, "
            f"Codex {perf_counter() - notion_finished:.2f}s"
        )
        typer.echo(visible_answer(answer, lookup))


@app.command()
def verify(question: str = "Knot 문서 내용 요약해줘") -> None:
    """Verify the live n8n → Codex path; no Discord message is sent."""
    configure_logging()
    try:
        anyio.run(verify_flow, question)
    except (AssistantFailure, httpx2.HTTPError, ValidationError) as error:
        failure = describe_failure(error, "조회·답변 검증")
        typer.echo(failure.message, err=True)
        raise typer.Exit(code=1) from None


@app.command("install-service")
def install_service() -> None:
    Settings()
    if get_keychain_token() is None:
        raise typer.BadParameter("먼저 `uv run --script bot.py set-token`으로 Discord 토큰을 저장해줘.")
    service_path = install_launch_agent(SCRIPT_DIR)
    typer.echo(f"로그인 시 자동 실행하도록 등록했어: {service_path}")


@app.command("evaluate")
def evaluate_command(rounds: int = typer.Option(2, min=1, max=3)) -> None:
    """Run repeated live retrieval/model checks without posting to Discord."""
    from evaluation import evaluate

    configure_logging()
    path, passed = anyio.run(evaluate, rounds)
    typer.echo(f"검증 {'PASS' if passed else 'FAIL'}: {path}")
    if not passed:
        raise typer.Exit(1)


@app.command("uninstall-service")
def uninstall_service() -> None:
    service_path = uninstall_launch_agent()
    typer.echo(f"자동 실행을 해제했어: {service_path}")


if __name__ == "__main__":
    app()
