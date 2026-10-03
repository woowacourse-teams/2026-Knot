from __future__ import annotations

from enum import StrEnum
from uuid import UUID

from pydantic import AliasChoices, BaseModel, ConfigDict, Field, ValidationInfo, field_validator

from settings import AssistantFailure, NotionLookup


class ReadModel(BaseModel):
    model_config = ConfigDict(frozen=True, extra="ignore")


class RichText(ReadModel):
    plain_text: str = ""


class NamedValue(ReadModel):
    name: str


class DateValue(ReadModel):
    start: str
    end: str | None = None


class PageProperty(ReadModel):
    type: str = ""
    title: list[RichText] = Field(default_factory=list)
    rich_text: list[RichText] = Field(default_factory=list)
    select: NamedValue | None = None
    status: NamedValue | None = None
    multi_select: list[NamedValue] = Field(default_factory=list)
    date: DateValue | None = None
    number: float | None = None
    checkbox: bool | None = None
    url: str | None = None

    def text(self) -> str:
        values = [text.plain_text for text in self.title + self.rich_text]
        values += [value.name for value in self.multi_select]
        values += [value.name for value in (self.select, self.status) if value is not None]
        if self.date is not None:
            values += [self.date.start, self.date.end or ""]
        if self.number is not None:
            values.append(str(self.number))
        if self.checkbox is not None:
            values.append(str(self.checkbox))
        if self.url is not None:
            values.append(self.url)
        return " ".join(value for value in values if value)


class Parent(ReadModel):
    page_id: UUID | None = None
    block_id: UUID | None = None
    database_id: UUID | None = None
    data_source_id: UUID | None = None


class BlockBody(ReadModel):
    rich_text: list[RichText] = Field(default_factory=list)
    title: str = ""
    cells: list[list[RichText]] = Field(default_factory=list)
    synced_from: Parent | None = None


class DataSourceRef(ReadModel):
    id: UUID


class Record(ReadModel):
    object: str
    id: UUID
    parent: Parent = Field(default_factory=Parent)
    title: list[RichText] = Field(default_factory=list)
    properties: dict[str, PageProperty] = Field(default_factory=dict)
    data_sources: list[DataSourceRef] = Field(default_factory=list)
    type: str = ""
    has_children: bool = False
    archived: bool = False
    in_trash: bool = False
    body: BlockBody = Field(
        default_factory=BlockBody,
        validation_alias=AliasChoices(
            "paragraph", "heading_1", "heading_2", "heading_3", "bulleted_list_item",
            "numbered_list_item", "to_do", "toggle", "code", "quote", "callout",
            "table_row", "synced_block", "child_page", "child_database", "equation",
        ),
    )

    @field_validator("properties", mode="before")
    @classmethod
    def page_properties_only(cls, value: object, info: ValidationInfo) -> object:
        return value if info.data.get("object") == "page" else {}

    def page_title(self) -> str:
        title = "".join(item.plain_text for item in self.title)
        for prop in self.properties.values():
            if prop.title:
                return "".join(item.plain_text for item in prop.title)
        return title or self.body.title or "제목 없음"


class RecordList(ReadModel):
    results: list[Record]
    has_more: bool = False
    next_cursor: UUID | None = None


class SearchFilter(ReadModel):
    value: str = "page"
    property: str = "object"


class PageQuery(ReadModel):
    query: str | None = None
    page_size: int = 100
    start_cursor: UUID | None = None
    filter: SearchFilter | None = None


class ToolOperation(StrEnum):
    SEARCH = "search"
    PAGE = "page"
    DATABASE = "database"


class ToolResponse(ReadModel):
    lookup: NotionLookup | None = None
    error_code: str | None = None


def parse_notion_id(value: str) -> UUID:
    try:
        return UUID(value)
    except ValueError as error:
        raise AssistantFailure("notion_invalid_id") from error
