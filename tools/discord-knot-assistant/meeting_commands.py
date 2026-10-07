from __future__ import annotations

import discord
import aiohttp
import structlog
from discord import app_commands

from meeting_access import meeting_context, meeting_notice
from meeting_forms import MeetingBasicsModal
from meeting_models import MeetingFailure
from meeting_session import MeetingSession
from meeting_repository import MeetingRepository
from meeting_views import render_session
from settings import Settings


class MeetingCommands(app_commands.Group):
    def __init__(self, settings: Settings, repository: MeetingRepository) -> None:
        super().__init__(name="회의", description="회의 공지를 작성해요")
        self.settings = settings
        self.repository = repository

    @app_commands.command(name="생성", description="회의 정보를 폼으로 작성하고 회의 일정 채널에 공지해요")
    @app_commands.guild_only()
    async def create(self, interaction: discord.Interaction) -> None:
        try:
            meeting_context(interaction, self.settings)
        except MeetingFailure as error:
            await meeting_notice(interaction, error.message)
            return
        if interaction.channel_id is None:
            await meeting_notice(interaction, "회의를 공지할 텍스트 채널에서 실행해줘.")
            return
        session = MeetingSession(self.settings, interaction.user.id, interaction.channel_id, self.repository)
        await interaction.response.send_modal(MeetingBasicsModal(session, session.revision, render_session))

    async def on_error(self, interaction: discord.Interaction, error: app_commands.AppCommandError) -> None:
        structlog.get_logger("knot_assistant").error("meeting.failed", stage="command", error_type=type(error).__name__)
        await meeting_notice(interaction, "회의 명령을 시작하는 중 오류가 났어. 잠깐 뒤 /회의 생성으로 다시 실행해줘.")


async def sync_meeting_commands(tree: app_commands.CommandTree, guild_id: int) -> None:
    log = structlog.get_logger("knot_assistant")
    guild = discord.Object(id=guild_id)
    try:
        existing = await tree.fetch_commands(guild=guild)
        if any(command.name != "회의" or command.type != discord.AppCommandType.chat_input for command in existing):
            log.error("meeting.failed", stage="command_sync", failure_code="existing_commands_conflict", guild_id=guild_id)
            return
        synced = await tree.sync(guild=guild)
    except discord.HTTPException as error:
        log.error("meeting.failed", stage="command_sync", failure_code=f"discord_http_{error.status}", guild_id=guild_id)
        return
    except (aiohttp.ClientError, OSError, TimeoutError) as error:
        log.error("meeting.failed", stage="command_sync", failure_code="transport_error", error_type=type(error).__name__, guild_id=guild_id)
        return
    log.info("meeting.commands.synced", guild_id=guild_id, command_count=len(synced))
