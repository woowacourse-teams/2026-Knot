from __future__ import annotations

import getpass
import os
import plistlib
import shutil
import subprocess
from pathlib import Path


LABEL = "com.knot.discord-assistant"


def install_launch_agent(script_dir: Path) -> Path:
    uv = shutil.which("uv")
    codex = shutil.which("codex")
    if uv is None or codex is None:
        raise FileNotFoundError("uv와 codex CLI가 PATH에 있어야 해.")

    home = Path.home()
    log_dir = home / "Library" / "Logs" / "KnotDiscordAssistant"
    log_dir.mkdir(parents=True, exist_ok=True)
    agent_dir = home / "Library" / "LaunchAgents"
    agent_dir.mkdir(parents=True, exist_ok=True)
    plist_path = agent_dir / f"{LABEL}.plist"
    path = os.pathsep.join((str(Path(uv).parent), str(Path(codex).parent), "/usr/bin", "/bin"))
    config = {
        "Label": LABEL,
        "ProgramArguments": [uv, "run", "--script", str(script_dir / "bot.py"), "run"],
        "WorkingDirectory": str(script_dir),
        "RunAtLoad": True,
        "KeepAlive": True,
        "ThrottleInterval": 10,
        "EnvironmentVariables": {"PATH": path, "KNOT_ASSISTANT_SERVICE": "1"},
        "StandardOutPath": str(log_dir / "stdout.log"),
        "StandardErrorPath": str(log_dir / "stderr.log"),
    }
    plist_path.write_bytes(plistlib.dumps(config))
    domain = f"gui/{os.getuid()}"
    subprocess.run(["launchctl", "bootout", domain, str(plist_path)], check=False, capture_output=True)
    subprocess.run(["launchctl", "bootstrap", domain, str(plist_path)], check=True)
    return plist_path


def uninstall_launch_agent() -> Path:
    home = Path.home()
    plist_path = home / "Library" / "LaunchAgents" / f"{LABEL}.plist"
    domain = f"gui/{os.getuid()}"
    subprocess.run(["launchctl", "bootout", domain, str(plist_path)], check=False, capture_output=True)
    plist_path.unlink(missing_ok=True)
    return plist_path
