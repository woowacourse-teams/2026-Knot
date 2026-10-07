from datetime import datetime, timedelta
import re

import discord

from meeting_models import KOREA, MeetingFailure


def meeting_date_options(selected: str) -> list[discord.SelectOption]:
    today = datetime.now(KOREA).date()
    dates = [today + timedelta(days=offset) for offset in range(25)]
    if selected and selected not in {date.isoformat() for date in dates}:
        dates[-1] = datetime.fromisoformat(selected).date()
    return [discord.SelectOption(label=f"{date:%m-%d} ({'월화수목금토일'[date.weekday()]})", value=date.isoformat(), default=selected == date.isoformat()) for date in dates]


def schedule_value(date: str, time: str) -> str:
    text = time.strip()
    if re.fullmatch(r"[0-9]{4}", text):
        text = f"{text[:2]}:{text[2:]}"
    if not re.fullmatch(r"[0-9]{2}:[0-9]{2}", text):
        raise MeetingFailure("schedule_input", "시간은 2030 또는 20:30처럼 시·분만 입력해줘. 한국 시간이야.")
    return f"{date} {text}"
