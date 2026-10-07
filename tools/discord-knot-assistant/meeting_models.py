from __future__ import annotations

from datetime import datetime
from enum import StrEnum
import re
from typing import Annotated, Final, assert_never
from zoneinfo import ZoneInfo

from pydantic import AwareDatetime, BaseModel, ConfigDict, StringConstraints, ValidationInfo, field_validator
from pydantic_core import PydanticCustomError

KOREA: Final = ZoneInfo("Asia/Seoul")
MEETING_TIME_FORMAT: Final = "%Y-%m-%d %H:%M"
MEETING_FORM_TIME_FORMAT: Final = "%m-%d %H:%M"


class MeetingKind(StrEnum):
    REGULAR = "정규"
    URGENT = "긴급"
    BACKEND = "백엔드"
    FRONTEND = "프론트엔드"


class MeetingAudience(StrEnum):
    FE = "FE"
    BE = "BE"
    EVERYONE = "Everyone"


class MeetingFailure(Exception):
    def __init__(self, code: str, message: str) -> None:
        self.code = code
        self.message = message
        super().__init__(code)


class MeetingSchedule(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    starts_at: AwareDatetime

    @field_validator("starts_at", mode="before")
    @classmethod
    def parse_time(cls, value: str | datetime, info: ValidationInfo) -> datetime:
        match value:
            case str():
                if info.mode == "json":
                    return datetime.fromisoformat(value)
                text = value.strip()
                if re.fullmatch(r"[0-9]{8}", text):
                    digits = f"{datetime.now(KOREA).year:04d}{text}"
                elif re.fullmatch(r"[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}", text):
                    digits = f"{datetime.now(KOREA).year:04d}{text.replace('-', '').replace(' ', '').replace(':', '')}"
                elif re.fullmatch(r"[0-9]{12}", text):
                    digits = text
                elif re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}", text):
                    digits = text.replace("-", "").replace(" ", "").replace(":", "")
                else:
                    raise PydanticCustomError("meeting_time_format", "월·일·시·분을 숫자 8자리로 입력해줘. 예: 10082000 → 10-08 20:00. MM-DD HH:MM 형식도 가능해. 연도는 올해야.")
                hour, minute = int(digits[8:10]), int(digits[10:12])
                if hour > 23 or minute > 59:
                    raise PydanticCustomError("meeting_time_clock", "시각을 확인해줘. 시는 00~23, 분은 00~59까지 입력할 수 있어.")
                try:
                    return datetime(int(digits[:4]), int(digits[4:6]), int(digits[6:8]), hour, minute, tzinfo=KOREA)
                except ValueError as error:
                    raise PydanticCustomError("meeting_time_date", "날짜를 확인해줘. 실제로 존재하는 연도·월·일을 입력해야 해.") from error
            case datetime():
                return value
            case unreachable:
                assert_never(unreachable)

    def ensure_future(self, now: datetime) -> None:
        if self.starts_at <= now:
            raise MeetingFailure("past_time", "일시는 현재보다 뒤여야 해. 기본 정보에서 일시를 수정해줘.")


class MeetingBasics(MeetingSchedule):
    kind: MeetingKind
    audience: MeetingAudience
    location: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=200)]


class MeetingDraft(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    basics: MeetingBasics
    topic: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=150)]
    decisions: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=1000)]
