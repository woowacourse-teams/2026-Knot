from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime
from enum import StrEnum
from time import monotonic
from typing import Final, NewType, assert_never

import anyio
import discord

from meeting_models import MeetingBasics, MeetingDraft, MeetingFailure
from meeting_repository import MeetingRepository
from settings import Settings

UserId = NewType("UserId", int)
ChannelId = NewType("ChannelId", int)
SESSION_SECONDS: Final = 600


@dataclass(frozen=True, slots=True)
class MeetingForm:
    when: str = ""
    date_choice: str = ""
    kind: str = ""
    audience: str = ""
    location: str = ""
    topic: str = ""
    decisions: str = ""


class MeetingState(StrEnum):
    OPEN = "open"
    POSTING = "posting"
    POSTED = "posted"
    CANCELLED = "cancelled"
    EXPIRED = "expired"
    FAILED = "failed"


class MeetingSession:
    """작성 중 값과 버튼의 세대를 바꾸며 한 초안의 전송 상태를 관리한다."""

    def __init__(self, settings: Settings, owner_id: int, channel_id: int, repository: MeetingRepository) -> None:
        self.settings = settings
        self.owner_id = UserId(owner_id)
        self.channel_id = ChannelId(channel_id)
        self.form = MeetingForm()
        self.basics: MeetingBasics | None = None
        self.starts_at: datetime | None = None
        self.draft: MeetingDraft | None = None
        self.revision = 0
        self.state = MeetingState.OPEN
        self.expires_at = monotonic() + SESSION_SECONDS
        self.lock = anyio.Lock()
        self.repository = repository
        self.editing_message_id: int | None = None
        self.editing_revision: int | None = None

    def remaining(self) -> float:
        return max(0.01, self.expires_at - monotonic())

    def check(self, interaction: discord.Interaction, revision: int) -> None:
        if interaction.user.id != self.owner_id:
            raise MeetingFailure("wrong_owner", "이 회의 초안은 작성자만 처리할 수 있어.")
        if interaction.channel_id != self.channel_id or interaction.guild_id != self.settings.discord_guild_id:
            raise MeetingFailure("wrong_channel", "회의를 작성한 서버·채널에서 다시 실행해줘.")
        if self.state == MeetingState.OPEN and monotonic() >= self.expires_at:
            self.state = MeetingState.EXPIRED
        if revision != self.revision:
            raise MeetingFailure("stale_form", "이전 단계의 초안이야. 가장 최근 폼이나 미리보기를 사용해줘.")
        match self.state:
            case MeetingState.OPEN:
                return
            case MeetingState.POSTING:
                message = "회의 공지를 전송 중이야. 잠깐 기다려줘."
            case MeetingState.POSTED:
                message = "이미 공지한 회의야. 같은 초안은 다시 전송하지 않아."
            case MeetingState.CANCELLED:
                message = "취소한 초안이야. 필요하면 /회의 생성으로 새로 작성해줘."
            case MeetingState.EXPIRED:
                message = "초안이 만료됐어. /회의 생성으로 다시 작성해줘."
            case MeetingState.FAILED:
                message = "이 초안의 전송은 종료됐어. 채널의 게시 여부를 확인한 뒤 새로 작성해줘."
            case unreachable:
                assert_never(unreachable)
        raise MeetingFailure(self.state.value, message)

    def begin_edit(self) -> int:
        self.revision += 1
        self.draft = None
        return self.revision

    def update_basics(self, when: str, kind: str, audience: str, location: str) -> None:
        self.form = replace(self.form, when=when, kind=kind, audience=audience, location=location)
        self.basics = None
        self.draft = None

    def update_agenda(self, topic: str, decisions: str) -> None:
        self.form = replace(self.form, topic=topic, decisions=decisions)
        self.draft = None
