from __future__ import annotations

from datetime import datetime

import aiohttp
import discord
import structlog

from meeting_access import meeting_context, meeting_embed, meeting_notice, meeting_recipient, safe_meeting_text
from meeting_forms import MeetingAgendaModal, MeetingBasicsModal
from meeting_models import KOREA, MeetingFailure
from meeting_published import MeetingPublishedView, change_published
from meeting_repository import MeetingRecord
from meeting_session import MeetingSession, MeetingState


class MeetingView(discord.ui.View):
    def __init__(self, session: MeetingSession) -> None:
        super().__init__(timeout=session.remaining())
        self.session = session
        self.revision = session.revision
        self.message: discord.InteractionMessage | None = None
        self.log = structlog.get_logger("knot_assistant")

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        try:
            self.session.check(interaction, self.revision)
        except MeetingFailure as error:
            await meeting_notice(interaction, error.message)
            return False
        return True

    async def on_error(self, interaction: discord.Interaction, error: Exception, item: discord.ui.Item) -> None:
        if self.session.state == MeetingState.POSTING:
            self.session.state = MeetingState.FAILED
            message = "회의 공지 전송 과정에서 오류가 났어. 채널에 공지가 올라갔는지 확인해줘. 중복을 막기 위해 다시 보내지 않아."
        elif self.session.state == MeetingState.POSTED:
            message = f"회의 공지는 반영했지만 후속 처리에서 오류가 났어. {error.message if isinstance(error, MeetingFailure) else '공지 채널에서 확인해줘.'}"
        else:
            message = error.message if isinstance(error, MeetingFailure) else "회의 버튼 처리에서 오류가 났어. /회의 생성으로 다시 작성해줘."
        self.log.error("meeting.failed", stage="button", failure_code=error.code if isinstance(error, MeetingFailure) else type(error).__name__, error_type=type(error).__name__)
        await meeting_notice(interaction, message)

    async def on_timeout(self) -> None:
        async with self.session.lock:
            if self.revision != self.session.revision or self.session.state != MeetingState.OPEN:
                return
            self.session.state = MeetingState.EXPIRED
            if self.message is not None:
                try:
                    await self.message.edit(content="회의 초안이 만료됐어. /회의 생성으로 다시 작성해줘.", view=None)
                except discord.HTTPException as error:
                    self.log.warning("meeting.failed", stage="expiry_notice", failure_code=f"discord_http_{error.status}")

    async def edit_basics(self, interaction: discord.Interaction) -> None:
        async with self.session.lock:
            self.session.check(interaction, self.revision)
            self.revision = self.session.begin_edit()
            await interaction.response.send_modal(MeetingBasicsModal(self.session, self.revision, render_session))

    async def edit_agenda(self, interaction: discord.Interaction) -> None:
        async with self.session.lock:
            self.session.check(interaction, self.revision)
            if self.session.basics is None:
                raise MeetingFailure("basics_required", "기본 정보를 먼저 수정해줘.")
            self.revision = self.session.begin_edit()
            await interaction.response.send_modal(MeetingAgendaModal(self.session, self.revision, render_session))

    async def cancel(self, interaction: discord.Interaction) -> None:
        async with self.session.lock:
            self.session.check(interaction, self.revision)
            self.session.state = MeetingState.CANCELLED
            message = "수정을 취소했어. 기존 회의 공지는 유지돼." if self.session.editing_message_id is not None else "회의 작성을 취소했어. 공지는 보내지 않았어."
            await interaction.response.edit_message(content=message, embed=None, view=None)
            self.stop()


class MeetingFormView(MeetingView):
    def __init__(self, session: MeetingSession) -> None:
        super().__init__(session)
        self.agenda_button.disabled = session.basics is None
        self.agenda_button.label = "다음 · 안건 작성"
        if session.basics is None:
            self.basics_button.label = "기본 정보 수정"
            self.basics_button.style = discord.ButtonStyle.primary

    @discord.ui.button(label="안건 작성", style=discord.ButtonStyle.primary)
    async def agenda_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self.edit_agenda(interaction)

    @discord.ui.button(label="기본 정보 수정", style=discord.ButtonStyle.secondary)
    async def basics_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self.edit_basics(interaction)

    @discord.ui.button(label="취소", style=discord.ButtonStyle.secondary)
    async def cancel_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self.cancel(interaction)


class MeetingPreview(MeetingView):
    def __init__(self, session: MeetingSession) -> None:
        super().__init__(session)
        if session.editing_message_id is not None:
            self.confirm_button.label = "수정 확정"
        else:
            self.confirm_button.label = "공지 확정"

    @discord.ui.button(label="확정 · 공지하기", style=discord.ButtonStyle.success)
    async def confirm_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await interaction.response.defer(ephemeral=True, thinking=True)
        async with self.session.lock:
            self.session.check(interaction, self.revision)
            draft = self.session.draft
            if draft is None:
                raise MeetingFailure("draft_required", "수정 중인 초안이야. 입력 폼을 제출한 뒤 새 미리보기에서 확정해줘.")
            draft.basics.ensure_future(datetime.now(KOREA))
            context = meeting_context(interaction, self.session.settings)
            recipient = meeting_recipient(context, draft.basics.audience, self.session.settings)
            self.session.state = MeetingState.POSTING
            if self.session.editing_message_id is not None and self.session.editing_revision is not None:
                try:
                    message = await change_published(interaction, self.session.settings, self.session.repository, self.session.editing_message_id, self.session.editing_revision, draft=draft)
                except MeetingFailure:
                    self.session.state = MeetingState.FAILED
                    raise
                self.session.state = MeetingState.POSTED
                await interaction.edit_original_response(content=f"회의 내용을 수정했어. [공지 확인]({message.jump_url})", view=None)
                if self.message is not None:
                    await self.message.edit(content="회의 내용을 수정했어.", view=None)
                self.stop()
                return
            try:
                message = await context.channel.send(
                    recipient.mention,
                    embed=meeting_embed(draft, context.author.display_name),
                    allowed_mentions=recipient.allowed_mentions,
                    nonce=interaction.id,
                    view=MeetingPublishedView(self.session.settings, self.session.repository, self.session.owner_id, render_session),
                )
            except discord.HTTPException as error:
                self.session.state = MeetingState.FAILED
                code = f"discord_http_{error.status}"
                detail = "Discord가 전송을 거부했어. 채널·멘션 권한을 확인해줘." if 400 <= error.status < 500 else "전송 성공 여부를 확인하지 못했어. 채널에 공지가 올라갔는지 확인해줘."
                self.log.error("meeting.failed", stage="publish", failure_code=code, channel_id=context.channel.id)
                await interaction.edit_original_response(content=f"회의 공지 전송에 실패했어. {detail} 이 초안은 다시 보내지 않아.", view=None)
                return
            except (aiohttp.ClientError, OSError, TimeoutError) as error:
                self.session.state = MeetingState.FAILED
                self.log.error("meeting.failed", stage="publish", failure_code="transport_error", error_type=type(error).__name__)
                await interaction.edit_original_response(content="Discord 연결 오류로 전송 성공 여부를 확인하지 못했어. 채널의 공지를 확인해줘. 중복을 막기 위해 이 초안은 다시 보내지 않아.", view=None)
                return
            self.session.state = MeetingState.POSTED
            await self.session.repository.save(MeetingRecord(message_id=message.id, channel_id=message.channel.id, owner_id=self.session.owner_id, owner_name=context.author.display_name, draft=draft))
            self.log.info("meeting.published", message_id=message.id, channel_id=message.channel.id, audience=draft.basics.audience.value)
            await interaction.edit_original_response(content=f"회의를 공지했어. [공지 확인]({message.jump_url})", view=None)
            if self.message is not None:
                await self.message.edit(content="회의를 공지했어.", view=None)
            self.stop()

    @discord.ui.button(label="기본 정보 수정", style=discord.ButtonStyle.secondary)
    async def basics_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self.edit_basics(interaction)

    @discord.ui.button(label="안건 수정", style=discord.ButtonStyle.secondary)
    async def agenda_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self.edit_agenda(interaction)

    @discord.ui.button(label="취소", style=discord.ButtonStyle.secondary)
    async def cancel_button(self, interaction: discord.Interaction, button: discord.ui.Button) -> None:
        await self.cancel(interaction)


async def render_session(interaction: discord.Interaction, session: MeetingSession, message: str) -> None:
    if session.draft is not None:
        embed = meeting_embed(session.draft, interaction.user.display_name, preview=True)
        view = MeetingPreview(session)
    else:
        embed = discord.Embed(title="회의 기본 정보", colour=discord.Colour.orange())
        if session.basics is not None:
            basics = session.basics
            embed.add_field(name="일시 · 한국 시간", value=basics.starts_at.astimezone(KOREA).strftime("%Y-%m-%d %H:%M"), inline=False)
            embed.add_field(name="구분 / 알림 대상", value=f"{basics.kind.value} / {basics.audience.value}", inline=False)
            embed.add_field(name="장소", value=safe_meeting_text(basics.location), inline=False)
        elif session.starts_at is not None:
            embed.add_field(name="일시 · 한국 시간", value=session.starts_at.astimezone(KOREA).strftime("%Y-%m-%d %H:%M"), inline=False)
        view = MeetingFormView(session)
    target_id = session.settings.discord_meeting_channel_id
    embed.add_field(name="공지할 채널", value=f"<#{target_id}>" if target_id is not None else "회의 공지 채널 미설정", inline=False)
    await interaction.response.send_message(message, embed=embed, view=view, ephemeral=True, allowed_mentions=discord.AllowedMentions.none())
    view.message = await interaction.original_response()
