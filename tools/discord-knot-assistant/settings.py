from __future__ import annotations

from pathlib import Path
from typing import Final

from pydantic import BaseModel, ConfigDict, Field, HttpUrl, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT_PAGE_ID: Final = "3aeb4351752280a29797de8949496356"
GUILD_ID: Final = 1536954284447633448
KEYCHAIN_SERVICE: Final = "com.knot.discord-assistant"
DEFAULT_CODEX_MODEL: Final = "gpt-5.6-luna"
SCRIPT_DIR: Final = Path(__file__).resolve().parent


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=SCRIPT_DIR / ".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    n8n_notion_webhook_url: HttpUrl = Field(validation_alias="N8N_NOTION_WEBHOOK_URL")
    n8n_webhook_secret: SecretStr = Field(validation_alias="N8N_WEBHOOK_SECRET")
    discord_guild_id: int = Field(default=GUILD_ID, validation_alias="DISCORD_GUILD_ID")
    discord_fe_role_id: int | None = Field(default=1536963487627087977, gt=0, validation_alias="DISCORD_FE_ROLE_ID")
    discord_be_role_id: int | None = Field(default=1536957281483366450, gt=0, validation_alias="DISCORD_BE_ROLE_ID")
    discord_meeting_channel_id: int | None = Field(default=1557003334567596173, gt=0, validation_alias="DISCORD_MEETING_CHANNEL_ID")
    notion_root_page_id: str = Field(default=ROOT_PAGE_ID, validation_alias="NOTION_ROOT_PAGE_ID")
    max_context_chars: int = Field(default=12000, ge=1000, le=30000, validation_alias="MAX_CONTEXT_CHARS")
    codex_model: str = Field(default=DEFAULT_CODEX_MODEL, validation_alias="CODEX_MODEL")

    @field_validator("n8n_notion_webhook_url")
    @classmethod
    def require_https(cls, value: HttpUrl) -> HttpUrl:
        if value.scheme != "https":
            raise ValueError("n8n webhook must use HTTPS")
        if value.host != "n8n.aitestbed.kr":
            raise ValueError("n8n webhook must use the configured Knot n8n host")
        return value


class NotionSource(BaseModel):
    model_config = ConfigDict(frozen=True, extra="ignore")

    title: str
    url: HttpUrl


class NotionCoverage(BaseModel):
    model_config = ConfigDict(frozen=True, extra="ignore")

    complete: bool
    documents: int
    requests: int
    cached: bool


class NotionLookup(BaseModel):
    model_config = ConfigDict(frozen=True, extra="ignore")

    context: str = ""
    sources: list[NotionSource] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    notices: list[str] = Field(default_factory=list)
    coverage: NotionCoverage | None = None
    pending: bool = False
    refreshing: bool = False


class CodexItem(BaseModel):
    model_config = ConfigDict(extra="ignore")

    type: str
    text: str | None = None


class CodexEvent(BaseModel):
    model_config = ConfigDict(extra="ignore")

    type: str
    item: CodexItem | None = None


class AssistantFailure(Exception):
    def __init__(self, code: str, user_message: str | None = None) -> None:
        self.code = code
        self.user_message = user_message
        super().__init__(code)
