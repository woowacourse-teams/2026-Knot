import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COMMON_SKILLS = ROOT / ".agents" / "skills"
BACKEND_SKILLS = ROOT / "backend" / ".agents" / "skills"
LEGACY_BACKEND_SKILLS = ROOT / "backend" / ".codex" / "skills"
EXPECTED_TRIGGERS = {
    "knot-commit": (
        "커밋해줘",
        "작업 단위로 커밋해줘",
        "커밋 메시지 작성해줘",
    ),
    "knot-pr": (
        "PR 본문 작성해줘",
        "PR 준비 상태 확인해줘",
        "PR 생성해줘",
        "PR 올려줘",
    ),
}
UNIX_ABSOLUTE_PATH = re.compile(
    r"(?<![A-Za-z0-9_$./-])/(?:[A-Za-z0-9._-]+/)*[A-Za-z0-9._-]+"
)
WINDOWS_ABSOLUTE_PATH = re.compile(r"[A-Za-z]:\\[^\s`\"']+")


def read_frontmatter(path):
    text = path.read_text(encoding="utf-8")
    parts = text.split("---", 2)
    if len(parts) != 3 or parts[0].strip():
        raise AssertionError(f"invalid frontmatter: {path}")

    fields = {}
    for line in parts[1].splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        fields[key.strip()] = value.strip()
    return fields, text


class BackendCodexSkillContractTest(unittest.TestCase):
    def test_backend_skills_use_official_module_discovery_path(self):
        self.assertFalse(LEGACY_BACKEND_SKILLS.exists())

        for name in EXPECTED_TRIGGERS:
            with self.subTest(skill=name):
                skill_dir = BACKEND_SKILLS / name
                self.assertTrue((skill_dir / "SKILL.md").is_file())
                metadata = skill_dir / "agents" / "openai.yaml"
                self.assertTrue(metadata.is_file())
                self.assertIn(
                    "allow_implicit_invocation: true",
                    metadata.read_text(encoding="utf-8"),
                )

    def test_skill_frontmatter_has_directory_name_and_description(self):
        for skill_file in BACKEND_SKILLS.glob("*/SKILL.md"):
            with self.subTest(skill=skill_file.parent.name):
                fields, _ = read_frontmatter(skill_file)
                self.assertEqual(skill_file.parent.name, fields.get("name"))
                self.assertTrue(fields.get("description"))
                self.assertRegex(fields["name"], r"^[a-z0-9-]+$")
                self.assertLessEqual(len(fields["name"]), 64)
                self.assertLessEqual(len(fields["description"]), 1024)

    def test_skill_names_are_unique_across_backend_discovery_chain(self):
        skill_files = list(COMMON_SKILLS.glob("*/SKILL.md"))
        skill_files.extend(BACKEND_SKILLS.glob("*/SKILL.md"))
        names = [read_frontmatter(path)[0].get("name") for path in skill_files]

        self.assertEqual(len(names), len(set(names)))

    def test_backend_skills_do_not_depend_on_external_absolute_paths(self):
        for skill_file in BACKEND_SKILLS.rglob("*"):
            if not skill_file.is_file():
                continue
            with self.subTest(path=skill_file.relative_to(ROOT)):
                text = skill_file.read_text(encoding="utf-8")
                self.assertIsNone(UNIX_ABSOLUTE_PATH.search(text))
                self.assertIsNone(WINDOWS_ABSOLUTE_PATH.search(text))

    def test_representative_korean_requests_route_unambiguously(self):
        descriptions = {
            name: read_frontmatter(BACKEND_SKILLS / name / "SKILL.md")[0][
                "description"
            ]
            for name in EXPECTED_TRIGGERS
        }

        for expected_skill, requests in EXPECTED_TRIGGERS.items():
            for request in requests:
                with self.subTest(request=request):
                    matches = [
                        name
                        for name, description in descriptions.items()
                        if request in description
                    ]
                    self.assertEqual([expected_skill], matches)

    def test_repository_guidance_explains_backend_skill_discovery(self):
        agents_md = (ROOT / "AGENTS.md").read_text(encoding="utf-8")

        self.assertIn("backend/.agents/skills", agents_md)
        self.assertIn("백엔드 작업은 `backend/`에서 시작", agents_md)
        self.assertIn("$knot-commit", agents_md)
        self.assertIn("$knot-pr", agents_md)


if __name__ == "__main__":
    unittest.main()
