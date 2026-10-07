from __future__ import annotations

from dataclasses import dataclass
from typing import assert_never

import discord

from meeting_models import KOREA, MeetingAudience, MeetingDraft, MeetingFailure
from settings import Settings


@dataclass(frozen=True, slots=True)
class MeetingContext:
    guild: discord.Guild
    channel: discord.TextChannel
    author: discord.Member
    bot: discord.Member


@dataclass(frozen=True, slots=True)
class MeetingRecipient:
    mention: str
    allowed_mentions: discord.AllowedMentions


def meeting_context(interaction: discord.Interaction, settings: Settings) -> MeetingContext:
    guild = interaction.guild
    if guild is None or guild.id != settings.discord_guild_id:
        raise MeetingFailure("wrong_guild", "Knot 서버의 텍스트 채널에서 /회의 생성으로 실행해줘.")
    channel = interaction.channel
    match channel:
        case discord.TextChannel() | discord.Thread():
            pass
        case _:
            raise MeetingFailure("unsupported_channel", "회의 공지는 텍스트 채널이나 스레드에서 작성할 수 있어.")
    author = interaction.user
    match author:
        case discord.Member():
            pass
        case _:
            raise MeetingFailure("member_required", "서버 멤버의 채널 권한을 확인할 수 없어.")
    bot = guild.me
    if bot is None:
        raise MeetingFailure("bot_member_missing", "서버에서 봇 권한을 확인할 수 없어. 다시 실행해줘.")
    target = guild.get_channel(settings.discord_meeting_channel_id) if settings.discord_meeting_channel_id is not None else None
    if not isinstance(target, discord.TextChannel):
        raise MeetingFailure("meeting_channel_missing", "회의 공지 채널을 찾을 수 없어. #회의-일정 채널 설정을 확인해줘.")
    for checked_channel in (channel, target):
        for member in (author, bot):
            permissions = checked_channel.permissions_for(member)
            match checked_channel:
                case discord.Thread():
                    can_send = permissions.send_messages_in_threads and not checked_channel.archived and (not checked_channel.locked or permissions.manage_threads)
                case discord.TextChannel():
                    can_send = permissions.send_messages
                case unreachable:
                    assert_never(unreachable)
            if not permissions.view_channel or not can_send:
                who = "봇" if member.id == bot.id else "작성자"
                raise MeetingFailure("channel_permission", f"{who}에게 #{checked_channel.name} 채널의 보기·메시지 전송 권한이 필요해.")
    if not target.permissions_for(bot).embed_links:
        raise MeetingFailure("embed_permission", "봇에게 #회의-일정 채널의 링크 임베드 권한이 필요해.")
    return MeetingContext(guild, target, author, bot)


def meeting_recipient(context: MeetingContext, audience: MeetingAudience, settings: Settings) -> MeetingRecipient:
    author_permissions = context.channel.permissions_for(context.author)
    bot_permissions = context.channel.permissions_for(context.bot)
    match audience:
        case MeetingAudience.EVERYONE:
            if not author_permissions.mention_everyone or not bot_permissions.mention_everyone:
                raise MeetingFailure("everyone_permission", "Everyone 공지는 작성자와 봇 모두에게 전체 멘션 권한이 필요해.")
            return MeetingRecipient("@everyone", discord.AllowedMentions(everyone=True, roles=False, users=False, replied_user=False))
        case MeetingAudience.FE:
            role_id = settings.discord_fe_role_id
        case MeetingAudience.BE:
            role_id = settings.discord_be_role_id
        case unreachable:
            assert_never(unreachable)
    role = context.guild.get_role(role_id) if role_id is not None else None
    if role is None or role.is_default():
        raise MeetingFailure("role_missing", f"{audience.value} 역할 설정을 확인할 수 없어. 역할 ID를 확인해줘.")
    if not role.mentionable and (not author_permissions.mention_everyone or not bot_permissions.mention_everyone):
        raise MeetingFailure("role_permission", f"{audience.value} 역할의 멘션 허용 또는 작성자·봇의 멘션 권한이 필요해.")
    return MeetingRecipient(role.mention, discord.AllowedMentions(everyone=False, roles=[role], users=False, replied_user=False))


def safe_meeting_text(value: str) -> str:
    return discord.utils.escape_markdown(discord.utils.escape_mentions(value))


def meeting_embed(draft: MeetingDraft, author_name: str, *, preview: bool = False) -> discord.Embed:
    basics = draft.basics
    embed = discord.Embed(
        title=f"{basics.audience.value} {basics.kind.value} 회의",
        description=f"**결정해야 할 것**\n{safe_meeting_text(draft.decisions)}",
        colour=discord.Colour.orange() if preview else discord.Colour.blue(),
    )
    embed.add_field(name="주제", value=safe_meeting_text(draft.topic), inline=False)
    embed.add_field(name="일시 · 한국 시간", value=basics.starts_at.astimezone(KOREA).strftime("%Y-%m-%d %H:%M"), inline=False)
    embed.add_field(name="장소", value=safe_meeting_text(basics.location), inline=False)
    embed.add_field(name="구분 / 알림 대상", value=f"{basics.kind.value} / {basics.audience.value}", inline=False)
    embed.set_footer(text="미리보기 · 확정 전에는 알리지 않음 · 초안은 생성 후 10분간 유효" if preview else f"작성자: {author_name}")
    return embed


async def meeting_notice(interaction: discord.Interaction, message: str) -> None:
    if interaction.response.is_done():
        await interaction.followup.send(message, ephemeral=True, allowed_mentions=discord.AllowedMentions.none())
    else:
        await interaction.response.send_message(message, ephemeral=True, allowed_mentions=discord.AllowedMentions.none())
