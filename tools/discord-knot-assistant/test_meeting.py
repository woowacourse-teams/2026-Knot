from __future__ import annotations

from datetime import datetime
from pathlib import Path
from unittest.mock import AsyncMock, Mock

import anyio
import discord
import pytest
from pydantic import ValidationError

from meeting_access import MeetingContext, MeetingRecipient, meeting_embed
from meeting_commands import MeetingCommands
from meeting_forms import MeetingAgendaModal, MeetingBasicsModal, invalid_fields
from meeting_models import KOREA, MeetingAudience, MeetingBasics, MeetingDraft, MeetingFailure, MeetingKind
from meeting_published import MeetingPublishedView, change_published, recover_record, record_embed
from meeting_repository import MeetingRecord, MeetingRepository
from meeting_session import MeetingSession, MeetingState
from meeting_views import MeetingPreview, render_session
from settings import Settings


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


def example_draft() -> MeetingDraft:
    return MeetingDraft(basics=MeetingBasics(starts_at="2099-10-08 20:00", kind=MeetingKind.BACKEND, audience=MeetingAudience.BE, location="Discord 음성 채널"), topic="회의", decisions="결정 사항")


def example_settings() -> Settings:
    return Settings(_env_file=None, N8N_NOTION_WEBHOOK_URL="https://n8n.aitestbed.kr/webhook/test", N8N_WEBHOOK_SECRET="synthetic-test-secret")


@pytest.mark.anyio
async def test_saved_meeting_survives_repository_restart(tmp_path: Path) -> None:
    path = tmp_path / "meetings.sqlite3"
    repository = MeetingRepository(path)
    record = MeetingRecord(message_id=100, channel_id=200, owner_id=300, owner_name="작성자", draft=example_draft())
    await repository.save(record)
    restarted = MeetingRepository(path)
    assert await restarted.get(100) == record
    assert await restarted.records() == [record]
    assert path.stat().st_mode & 0o777 == 0o600


@pytest.mark.parametrize("when", ["2026-02-30 20:00", "다음 목요일", "2026-10-08T20:00+09:00"])
def test_form_rejects_invalid_or_non_form_time(when: str) -> None:
    with pytest.raises(ValidationError):
        MeetingBasics(starts_at=when, kind=MeetingKind.REGULAR, audience=MeetingAudience.FE, location="장소")


def test_past_time_and_empty_required_field_are_rejected() -> None:
    draft = example_draft()
    with pytest.raises(MeetingFailure, match="past_time"):
        draft.basics.ensure_future(datetime(2100, 1, 1, tzinfo=KOREA))
    with pytest.raises(ValidationError):
        MeetingDraft(basics=draft.basics, topic=" ", decisions="안건")


@pytest.mark.parametrize("when", ["209910082000", "2099-10-08 20:00", " 209910082000 "])
def test_numeric_time_matches_formatted_time(when: str) -> None:
    basics = MeetingBasics(starts_at=when, kind=MeetingKind.REGULAR, audience=MeetingAudience.FE, location="장소")
    assert basics.starts_at == datetime(2099, 10, 8, 20, 0, tzinfo=KOREA)


@pytest.mark.parametrize("when", ["10082000", "10-08 20:00"])
def test_year_is_automatically_current_korean_year(when: str) -> None:
    basics = MeetingBasics(starts_at=when, kind=MeetingKind.REGULAR, audience=MeetingAudience.FE, location="장소")
    assert basics.starts_at == datetime(datetime.now(KOREA).year, 10, 8, 20, 0, tzinfo=KOREA)


@pytest.mark.parametrize("when, message", [("20261212312312", "숫자 8자리"), ("12123123", "시는 00~23"), ("02302000", "실제로 존재하는"), ("12122060", "분은 00~59")])
def test_numeric_time_errors_explain_the_invalid_part(when: str, message: str) -> None:
    with pytest.raises(ValidationError) as failure:
        MeetingBasics(starts_at=when, kind=MeetingKind.REGULAR, audience=MeetingAudience.FE, location="장소")
    assert message in invalid_fields(failure.value)


@pytest.mark.anyio
async def test_command_forms_and_persistent_buttons(tmp_path: Path) -> None:
    settings = example_settings()
    repository = MeetingRepository(tmp_path / "meetings.sqlite3")
    session = MeetingSession(settings, 300, 200, repository)
    command = MeetingCommands(settings, repository)
    assert command.name == "회의"
    assert [item.name for item in command.commands] == ["생성"]
    assert command.commands[0].parameters == []
    basics = MeetingBasicsModal(session, 0, render_session)
    agenda = MeetingAgendaModal(session, 0, render_session)
    assert len(basics.children) == 5
    assert len(agenda.children) == 2
    assert [option.value for option in basics.kind.options] == ["정규", "긴급", "백엔드", "프론트엔드"]
    assert [option.value for option in basics.audience.options] == ["FE", "BE", "Everyone"]
    published = MeetingPublishedView(settings, repository, 300, render_session)
    assert published.is_persistent()
    assert [item.custom_id for item in published.children] == ["knot:meeting:edit", "knot:meeting:cancel"]


def test_long_markdown_and_mentions_fit_embed_limits() -> None:
    draft = example_draft().model_copy(update={"topic": "*" * 150, "decisions": "*" * 1000})
    embed = meeting_embed(draft, "작성자")
    assert len(embed.title or "") <= 256
    assert len(embed.description or "") <= 4096
    assert all(len(field.value) <= 1024 for field in embed.fields)
    mention_draft = example_draft().model_copy(update={"decisions": "@everyone @here <@123> <@&456>"})
    safe = meeting_embed(mention_draft, "작성자").description or ""
    assert "@everyone" not in safe
    assert "@here" not in safe


def interaction_fixture(settings: Settings, *, owner_id: int = 300) -> Mock:
    interaction = Mock(spec=discord.Interaction)
    interaction.user = Mock(spec=discord.Member, id=owner_id, display_name="작성자")
    interaction.channel_id = 200
    interaction.guild_id = settings.discord_guild_id
    interaction.id = 999
    interaction.response = Mock(defer=AsyncMock())
    interaction.edit_original_response = AsyncMock()
    interaction.message = Mock(spec=discord.Message, edit=AsyncMock())
    return interaction


@pytest.mark.anyio
async def test_session_rejects_wrong_owner_stale_preview_and_expiry(tmp_path: Path) -> None:
    settings = example_settings()
    session = MeetingSession(settings, 300, 200, MeetingRepository(tmp_path / "meetings.sqlite3"))
    with pytest.raises(MeetingFailure, match="wrong_owner"):
        session.check(interaction_fixture(settings, owner_id=301), 0)
    assert session.state == MeetingState.OPEN
    session.begin_edit()
    with pytest.raises(MeetingFailure, match="stale_form"):
        session.check(interaction_fixture(settings), 0)
    session.expires_at = 0
    with pytest.raises(MeetingFailure, match="expired"):
        session.check(interaction_fixture(settings), 1)
    session.state = MeetingState.POSTED
    with pytest.raises(MeetingFailure, match="posted"):
        session.check(interaction_fixture(settings), 1)
    assert session.state == MeetingState.POSTED


@pytest.mark.anyio
async def test_concurrent_confirmation_sends_once_and_edits_ephemeral_response(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    settings = example_settings()
    repository = MeetingRepository(tmp_path / "meetings.sqlite3")
    session = MeetingSession(settings, 300, 200, repository)
    session.draft = example_draft()
    channel = Mock(spec=discord.TextChannel, id=200)
    sent = Mock(spec=discord.Message, id=100, channel=channel, jump_url="https://discord.com/channels/1/200/100")

    async def send(*args: str, **kwargs: discord.Embed) -> discord.Message:
        await anyio.sleep(0)
        return sent

    channel.send = AsyncMock(side_effect=send)
    author = Mock(spec=discord.Member, id=300, display_name="작성자")
    context = MeetingContext(Mock(spec=discord.Guild), channel, author, Mock(spec=discord.Member))
    monkeypatch.setattr("meeting_views.meeting_context", lambda *_: context)
    monkeypatch.setattr("meeting_views.meeting_recipient", lambda *_: MeetingRecipient("<@&123>", discord.AllowedMentions(everyone=False, users=False, roles=[discord.Object(id=123)], replied_user=False)))
    preview = MeetingPreview(session)
    preview.message = Mock(spec=discord.InteractionMessage, edit=AsyncMock())
    interactions = [interaction_fixture(settings), interaction_fixture(settings)]
    failures: list[str] = []

    async def confirm(interaction: discord.Interaction) -> None:
        try:
            await preview.confirm_button.callback(interaction)
        except MeetingFailure as error:
            failures.append(error.code)

    async with anyio.create_task_group() as group:
        for interaction in interactions:
            group.start_soon(confirm, interaction)
    channel.send.assert_awaited_once()
    preview.message.edit.assert_awaited_once()
    for interaction in interactions:
        interaction.message.edit.assert_not_awaited()
    assert failures == ["posted"]
    assert session.state == MeetingState.POSTED
    assert (await repository.get(100)).owner_id == 300


@pytest.mark.anyio
async def test_published_change_rejects_wrong_owner_or_old_revision(tmp_path: Path) -> None:
    settings = example_settings()
    repository = MeetingRepository(tmp_path / "meetings.sqlite3")
    record = MeetingRecord(message_id=100, channel_id=200, owner_id=300, owner_name="작성자", draft=example_draft(), revision=1)
    await repository.save(record)
    with pytest.raises(MeetingFailure, match="meeting_ended"):
        await change_published(interaction_fixture(settings, owner_id=301), settings, repository, 100, 1, draft=None)
    with pytest.raises(MeetingFailure, match="meeting_changed"):
        await change_published(interaction_fixture(settings), settings, repository, 100, 0, draft=None)
    assert await repository.get(100) == record


@pytest.mark.anyio
async def test_uncertain_change_is_recovered_from_existing_message(tmp_path: Path) -> None:
    repository = MeetingRepository(tmp_path / "meetings.sqlite3")
    changed = example_draft().model_copy(update={"topic": "변경된 주제"})
    record = MeetingRecord(message_id=100, channel_id=200, owner_id=300, owner_name="작성자", draft=example_draft(), pending_draft=changed)
    await repository.save(record)
    target = record.model_copy(update={"draft": changed})
    message = Mock(spec=discord.Message, embeds=[discord.Embed.from_dict(record_embed(target).to_dict())])
    resolved = await recover_record(record, message, repository)
    assert resolved.draft == changed
    assert resolved.revision == 1
    assert resolved.pending_draft is None
    assert await repository.get(100) == resolved
