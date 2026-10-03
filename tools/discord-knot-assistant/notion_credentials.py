# /// script
# requires-python = ">=3.11,<3.12"
# dependencies = ["keyring>=25,<27", "pydantic-settings>=2.8,<3"]
# ///

from __future__ import annotations

import getpass
import os
import sys
from typing import Final

import keyring
from keyring.backends.macOS import Keyring

from settings import AssistantFailure

NOTION_KEYCHAIN_SERVICE: Final = "com.knot.discord-assistant.notion"


def save_notion_token(token: str) -> None:
    keyring.set_keyring(Keyring())
    keyring.set_password(NOTION_KEYCHAIN_SERVICE, getpass.getuser(), token)


def load_notion_token() -> str:
    token = os.environ.get("NOTION_ACCESS_TOKEN")
    if token:
        return token
    keyring.set_keyring(Keyring())
    token = keyring.get_password(NOTION_KEYCHAIN_SERVICE, getpass.getuser())
    if not token:
        raise AssistantFailure("notion_token_missing")
    return token


if __name__ == "__main__":
    save_notion_token(sys.stdin.read().strip())
