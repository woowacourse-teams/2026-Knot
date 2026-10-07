from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import AsyncMock

import pytest

from meeting_forms import MeetingBasicsModal
from meeting_models import KOREA, MeetingFailure, MeetingSchedule
from meeting_repository import MeetingRepository
from meeting_session import MeetingSession
from meeting_time import meeting_date_options, schedule_value
from test_meeting import example_settings, interaction_fixture


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.mark.parametrize("time", ["2030", "20:30", " 2030 "])
def test_date_selection_and_time_input(time: str) -> None:
    assert schedule_value("2026-10-08", time) == "2026-10-08 20:30"


@pytest.mark.parametrize("time", ["", "8시", "2020:00", "10082030"])
def test_invalid_time_format_is_explained(time: str) -> None:
    with pytest.raises(MeetingFailure, match="schedule_input"):
        schedule_value("2026-10-08", time)


def test_saved_date_outside_window_remains_available_for_edit() -> None:
    options = meeting_date_options("2099-10-08")
    assert len(options) == 25
    assert [option.value for option in options if option.default] == ["2099-10-08"]


@pytest.mark.anyio
async def test_date_and_time_submission_preserves_values(monkeypatch: pytest.MonkeyPatch, tmp_path: Path) -> None:
    settings = example_settings()
    session = MeetingSession(settings, 300, 200, MeetingRepository(tmp_path / "meetings.sqlite3"))
    render = AsyncMock()
    modal = MeetingBasicsModal(session, 0, render)
    assert len(modal.children) == 5
    assert len(modal.date.options) == 25
    assert modal.time.min_length == 4
    assert modal.time.max_length == 5
    date = (datetime.now(KOREA) + timedelta(days=1)).date().isoformat()
    modal.date._values = [date]
    modal.time._value = "2030"
    modal.kind._values = ["백엔드"]
    modal.audience._values = ["BE"]
    modal.location._value = "Discord 음성 채널"
    monkeypatch.setattr("meeting_forms.meeting_context", lambda *_: None)
    monkeypatch.setattr("meeting_forms.meeting_recipient", lambda *_: None)
    await modal.on_submit(interaction_fixture(settings))
    assert session.starts_at == MeetingSchedule(starts_at=f"{date} 20:30").starts_at
    assert session.form.when == "20:30"
    assert session.revision == 1
    render.assert_awaited_once()
    reopened = MeetingBasicsModal(session, 1, render)
    assert [option.value for option in reopened.date.options if option.default] == [date]
    assert reopened.time.default == "20:30"
