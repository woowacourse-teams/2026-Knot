from __future__ import annotations

import aiohttp
import discord
import structlog

from meeting_access import meeting_context, meeting_embed, meeting_notice, meeting_recipient
from meeting_forms import MeetingBasicsModal, RenderSession
from meeting_models import KOREA, MeetingDraft, MeetingFailure
from meeting_repository import MeetingRecord, MeetingRepository
from meeting_session import MeetingForm, MeetingSession
from settings import Settings


def record_embed(record: MeetingRecord, *, cancelled: bool = False) -> discord.Embed:
    embed = meeting_embed(record.draft, record.owner_name)
    if cancelled:
        embed.title = f"취소됨 · {embed.title}"
        embed.colour = discord.Colour.red()
        embed.set_footer(text=f"작성자: {record.owner_name} · 작성자가 취소한 회의")
    return embed


def same_embed(actual: discord.Embed, expected: discord.Embed) -> bool:
    return (
        actual.title == expected.title and actual.description == expected.description
        and actual.colour == expected.colour and actual.footer.text == expected.footer.text
        and [(field.name, field.value, field.inline) for field in actual.fields] == [(field.name, field.value, field.inline) for field in expected.fields]
    )


async def recover_record(record: MeetingRecord, message: discord.Message, repository: MeetingRepository) -> MeetingRecord:
    if record.pending_draft is None and not record.pending_cancel:
        return record
    target = record.model_copy(update={"draft": record.pending_draft or record.draft})
    expected = record_embed(target, cancelled=record.pending_cancel)
    if message.embeds and same_embed(message.embeds[0], expected):
        resolved = target.model_copy(update={"revision": record.revision + 1, "cancelled": record.pending_cancel, "pending_draft": None, "pending_cancel": False})
    elif message.embeds and same_embed(message.embeds[0], record_embed(record)):
        resolved = record.model_copy(update={"pending_draft": None, "pending_cancel": False})
    else:
        raise MeetingFailure("meeting_recovery_conflict", "저장된 회의와 공지 상태가 달라. 자동 변경을 멈췄어. 운영 로그에서 회의 상태를 확인해줘.")
    await repository.save(resolved)
    return resolved


class MeetingPublishedView(discord.ui.View):
    def __init__(self, settings: Settings, repository: MeetingRepository, owner_id: int, render: RenderSession) -> None:
        super().__init__(timeout=None)
        self.settings = settings
        self.repository = repository
        self.owner_id = owner_id
        self.render = render

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        if interaction.user.id != self.owner_id:
            await meeting_notice(interaction, "회의를 예약한 사람만 수정하거나 취소할 수 있어.")
            return False
        return True

    async def on_error(self, interaction: discord.Interaction, error: Exception, item: discord.ui.Item) -> None:
        structlog.get_logger("knot_assistant").error("meeting.failed", stage="published_button", failure_code=error.code if isinstance(error, MeetingFailure) else type(error).__name__, error_type=type(error).__name__)
        await meeting_notice(interaction, error.message if isinstance(error, MeetingFailure) else "게시된 회의 처리에서 오류가 났어. 공지 상태를 확인한 뒤 다시 눌러줘.")

    async def current_record(self, interaction: discord.Interaction) -> MeetingRecord | None:
        if interaction.message is None:
            raise MeetingFailure("message_required", "회의 공지의 버튼에서 실행해줘.")
        record = await self.repository.get(interaction.message.id)
        if record.owner_id != interaction.user.id or record.cancelled:
            raise MeetingFailure("meeting_ended", "작성자가 아니거나 이미 취소된 회의야.")
        if record.pending_draft is not None or record.pending_cancel:
            await interaction.response.defer(ephemeral=True, thinking=True)
            async with self.repository.lock:
                current = await self.repository.get(record.message_id)
                message = await interaction.message.channel.fetch_message(record.message_id)
                await recover_record(current, message, self.repository)
            await interaction.edit_original_response(content="회의 상태를 확인했어. 공지의 버튼을 다시 눌러줘.")
            return None
        return record

    @discord.ui.button(label="회의 수정", style=discord.ButtonStyle.secondary, custom_id="knot:meeting:edit")
    async def edit_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        record = await self.current_record(interaction)
        if record is None or interaction.channel_id is None:
            return
        meeting_context(interaction, self.settings)
        session = MeetingSession(self.settings, record.owner_id, interaction.channel_id, self.repository)
        basics = record.draft.basics
        session.form = MeetingForm(when=basics.starts_at.astimezone(KOREA).strftime("%H:%M"), date_choice=basics.starts_at.astimezone(KOREA).date().isoformat(), kind=basics.kind.value, audience=basics.audience.value, location=basics.location, topic=record.draft.topic, decisions=record.draft.decisions)
        session.basics = basics
        session.starts_at = basics.starts_at
        session.editing_message_id = record.message_id
        session.editing_revision = record.revision
        await interaction.response.send_modal(MeetingBasicsModal(session, session.revision, self.render))

    @discord.ui.button(label="회의 취소", style=discord.ButtonStyle.danger, custom_id="knot:meeting:cancel")
    async def cancel_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        record = await self.current_record(interaction)
        if record is None:
            return
        meeting_context(interaction, self.settings)
        view = MeetingCancelView(self.settings, self.repository, record)
        await interaction.response.send_message("이 회의를 취소할까? 공지에는 취소됨으로 표시돼.", ephemeral=True, view=view)
        view.message = await interaction.original_response()


class MeetingCancelView(discord.ui.View):
    def __init__(self, settings: Settings, repository: MeetingRepository, record: MeetingRecord) -> None:
        super().__init__(timeout=600)
        self.settings = settings
        self.repository = repository
        self.record = record
        self.message: discord.InteractionMessage | None = None

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        if interaction.user.id != self.record.owner_id:
            await meeting_notice(interaction, "회의를 예약한 사람만 취소할 수 있어.")
            return False
        return True

    async def on_error(self, interaction: discord.Interaction, error: Exception, item: discord.ui.Item) -> None:
        structlog.get_logger("knot_assistant").error("meeting.failed", stage="cancel", failure_code=error.code if isinstance(error, MeetingFailure) else type(error).__name__, error_type=type(error).__name__)
        await meeting_notice(interaction, error.message if isinstance(error, MeetingFailure) else "회의 취소 처리에서 오류가 났어. 공지 상태를 확인해줘.")

    @discord.ui.button(label="회의 취소 확정", style=discord.ButtonStyle.danger)
    async def confirm(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        await change_published(interaction, self.settings, self.repository, self.record.message_id, self.record.revision, draft=None)
        await interaction.edit_original_response(content="회의를 취소했어. 공지에도 취소됨으로 표시했어.", view=None)
        if self.message is not None:
            await self.message.edit(content="회의를 취소했어.", view=None)
        self.stop()

    @discord.ui.button(label="돌아가기", style=discord.ButtonStyle.secondary)
    async def back(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await interaction.response.edit_message(content="취소하지 않았어.", view=None)
        self.stop()


async def change_published(interaction: discord.Interaction, settings: Settings, repository: MeetingRepository, message_id: int, revision: int, *, draft: MeetingDraft | None) -> discord.Message:
    async with repository.lock:
        record = await repository.get(message_id)
        if record.owner_id != interaction.user.id or record.cancelled:
            raise MeetingFailure("meeting_ended", "작성자가 아니거나 이미 취소된 회의야.")
        if record.revision != revision or record.pending_draft is not None or record.pending_cancel:
            raise MeetingFailure("meeting_changed", "회의가 이미 바뀌었거나 처리 중이야. 공지의 버튼에서 다시 시작해줘.")
        context = meeting_context(interaction, settings)
        if context.channel.id != record.channel_id:
            raise MeetingFailure("meeting_channel_changed", "공지 채널 설정이 바뀌었어. 기존 회의의 채널 설정을 확인해줘.")
        updated = record.model_copy(update={"draft": draft or record.draft, "cancelled": draft is None, "revision": revision + 1})
        recipient = meeting_recipient(context, updated.draft.basics.audience, settings) if draft is not None else None
        pending = record.model_copy(update={"pending_draft": draft, "pending_cancel": draft is None})
        await repository.save(pending)
        try:
            message = await context.channel.get_partial_message(message_id).edit(
                content=recipient.mention if recipient is not None else "회의 취소",
                embed=record_embed(updated, cancelled=updated.cancelled),
                allowed_mentions=discord.AllowedMentions.none(),
                view=None if updated.cancelled else discord.utils.MISSING,
            )
        except discord.HTTPException as error:
            if 400 <= error.status < 500:
                await repository.save(record)
            raise MeetingFailure(f"discord_http_{error.status}", "Discord에서 회의 변경을 완료하지 못했어. 공지 상태를 확인한 뒤 버튼을 다시 눌러줘.") from error
        except (aiohttp.ClientError, OSError, TimeoutError) as error:
            raise MeetingFailure("meeting_change_uncertain", "연결 오류로 변경 여부를 확인하지 못했어. 공지의 버튼을 다시 누르면 상태부터 확인할게.") from error
        await repository.save(updated)
        structlog.get_logger("knot_assistant").info("meeting.changed", message_id=message_id, cancelled=updated.cancelled, revision=updated.revision)
        return message


async def restore_meeting_views(client: discord.Client, settings: Settings, repository: MeetingRepository, render: RenderSession) -> None:
    log = structlog.get_logger("knot_assistant")
    try:
        records = await repository.records()
    except MeetingFailure as error:
        log.error("meeting.failed", stage="restore", failure_code=error.code)
        return
    count = 0
    for record in records:
        if record.cancelled:
            continue
        if record.pending_draft is not None or record.pending_cancel:
            try:
                channel = client.get_partial_messageable(record.channel_id)
                message = await channel.fetch_message(record.message_id)
                record = await recover_record(record, message, repository)
            except (MeetingFailure, discord.HTTPException, aiohttp.ClientError, OSError, TimeoutError) as error:
                log.error("meeting.failed", stage="restore_pending", message_id=record.message_id, error_type=type(error).__name__)
        if not record.cancelled:
            client.add_view(MeetingPublishedView(settings, repository, record.owner_id, render), message_id=record.message_id)
            count += 1
    log.info("meeting.views.restored", count=count)
