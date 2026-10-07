from __future__ import annotations

from collections.abc import Awaitable, Callable
from dataclasses import replace
from datetime import datetime
from typing import assert_never

import discord
import structlog
from pydantic import ValidationError

from meeting_access import meeting_context, meeting_notice, meeting_recipient
from meeting_models import KOREA, MeetingAudience, MeetingBasics, MeetingDraft, MeetingFailure, MeetingKind
from meeting_session import MeetingSession
from meeting_time import meeting_date_options, schedule_value

RenderSession = Callable[[discord.Interaction, MeetingSession, str], Awaitable[None]]


def invalid_fields(error: ValidationError) -> str:
    labels = {"starts_at": "일시", "kind": "구분", "audience": "대상", "location": "장소", "topic": "주제", "decisions": "결정해야 할 것"}
    messages = []
    for item in error.errors():
        field = str(item["loc"][-1])
        if field == "starts_at":
            messages.append(str(item["msg"]))
        else:
            messages.append(f"{labels.get(field, '입력')} 입력을 확인해줘. 필수 항목은 공백 없이 작성해줘.")
    return " ".join(dict.fromkeys(messages))


class MeetingModal(discord.ui.Modal):
    def __init__(self, session: MeetingSession, revision: int, render: RenderSession, title: str) -> None:
        super().__init__(title=title, timeout=session.remaining())
        self.session = session
        self.revision = revision
        self.render = render

    async def interaction_check(self, interaction: discord.Interaction) -> bool:
        try:
            self.session.check(interaction, self.revision)
        except MeetingFailure as error:
            await meeting_notice(interaction, error.message)
            return False
        return True

    async def on_error(self, interaction: discord.Interaction, error: Exception) -> None:
        structlog.get_logger("knot_assistant").error("meeting.failed", stage="form", error_type=type(error).__name__)
        await meeting_notice(interaction, "회의 입력 폼 처리에서 오류가 났어. /회의 생성으로 다시 작성해줘. 공지는 보내지 않았어.")


class MeetingBasicsModal(MeetingModal):
    def __init__(self, session: MeetingSession, revision: int, render: RenderSession) -> None:
        super().__init__(session, revision, render, "회의 기본 정보 · 1/2")
        form = session.form
        self.date = discord.ui.Select(placeholder="회의 날짜 선택", options=meeting_date_options(form.date_choice))
        self.time = discord.ui.TextInput(placeholder="2030 → 20:30", default=form.when, min_length=4, max_length=5)
        self.kind = discord.ui.Select(placeholder="회의 구분", options=[
            discord.SelectOption(label=kind.value, value=kind.value, default=form.kind == kind.value) for kind in MeetingKind
        ])
        self.audience = discord.ui.Select(placeholder="알림 대상", options=[
            discord.SelectOption(label=audience.value, value=audience.value, default=form.audience == audience.value) for audience in MeetingAudience
        ])
        self.location = discord.ui.TextInput(placeholder="회의실, Discord 음성 채널 또는 온라인 회의 링크", default=form.location, min_length=1, max_length=200)
        self.add_item(discord.ui.Label(text="날짜 · 한국 시간", description="오늘부터 25일 중 골라줘. 연도는 자동으로 포함돼.", component=self.date))
        self.add_item(discord.ui.Label(text="시간 · 한국 시간", description="시·분만 입력해줘. 예: 2030 또는 20:30", component=self.time))
        self.add_item(discord.ui.Label(text="구분", component=self.kind))
        self.add_item(discord.ui.Label(text="알림 대상", component=self.audience))
        self.add_item(discord.ui.Label(text="장소", component=self.location))

    async def on_submit(self, interaction: discord.Interaction) -> None:
        async with self.session.lock:
            self.session.check(interaction, self.revision)
            date = self.date.values[0]
            self.session.update_basics(str(self.time), self.kind.values[0], self.audience.values[0], str(self.location))
            self.session.form = replace(self.session.form, date_choice=date)
            self.session.starts_at = None
            form = self.session.form
            message = "기본 정보를 저장했어. 안건을 작성해줘. 초안은 처음 시작한 때부터 10분간 유효해."
            try:
                basics = MeetingBasics(starts_at=schedule_value(date, form.when), kind=form.kind, audience=form.audience, location=form.location)
                self.session.form = replace(form, when=basics.starts_at.strftime("%H:%M"))
                basics.ensure_future(datetime.now(KOREA))
                context = meeting_context(interaction, self.session.settings)
                meeting_recipient(context, basics.audience, self.session.settings)
                self.session.basics = basics
                self.session.starts_at = basics.starts_at
                if form.topic and form.decisions:
                    self.session.draft = MeetingDraft(basics=basics, topic=form.topic, decisions=form.decisions)
                    message = "수정한 기본 정보를 반영했어. 공지 내용을 확인해줘."
            except ValidationError as error:
                message = invalid_fields(error)
            except MeetingFailure as error:
                message = error.message
            self.session.revision += 1
            await self.render(interaction, self.session, message)


class MeetingAgendaModal(MeetingModal):
    def __init__(self, session: MeetingSession, revision: int, render: RenderSession) -> None:
        super().__init__(session, revision, render, "회의 안건 · 2/2")
        self.topic = discord.ui.TextInput(placeholder="어떤 주제로 회의할까?", default=session.form.topic, min_length=1, max_length=150)
        self.decisions = discord.ui.TextInput(style=discord.TextStyle.paragraph, placeholder="이번 회의에서 결정해야 할 것을 적어줘.", default=session.form.decisions, min_length=1, max_length=1000)
        self.add_item(discord.ui.Label(text="주제", component=self.topic))
        self.add_item(discord.ui.Label(text="결정해야 할 것", component=self.decisions))

    async def on_submit(self, interaction: discord.Interaction) -> None:
        async with self.session.lock:
            self.session.check(interaction, self.revision)
            self.session.update_agenda(str(self.topic), str(self.decisions))
            message = "아직 공지하지 않았어. 내용을 확인한 뒤 확정해줘."
            if self.session.editing_message_id is not None:
                message = "수정 내용을 확인한 뒤 확정해줘. 기존 공지는 확정 전까지 유지돼."
            try:
                match self.session.basics:
                    case MeetingBasics() as basics:
                        basics.ensure_future(datetime.now(KOREA))
                        self.session.draft = MeetingDraft(basics=basics, topic=self.session.form.topic, decisions=self.session.form.decisions)
                    case None:
                        raise MeetingFailure("basics_required", "기본 정보를 먼저 수정해줘.")
                    case unreachable:
                        assert_never(unreachable)
            except ValidationError as error:
                message = invalid_fields(error)
            except MeetingFailure as error:
                message = error.message
            self.session.revision += 1
            await self.render(interaction, self.session, message)
