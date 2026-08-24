import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SKILL_NAMES = ("knot-issue-planning", "knot-deep-interview", "knot-grill-me")


class AgentHarnessParityTest(unittest.TestCase):
    def test_claude_imports_shared_project_instructions(self):
        claude_md = (ROOT / "CLAUDE.md").read_text(encoding="utf-8")

        self.assertIn("@AGENTS.md", claude_md)
        self.assertIn("harness/issue_planning.py", claude_md)
        self.assertIn("harness/materialize_adr.py", claude_md)

    def test_claude_skills_point_to_canonical_agent_skills(self):
        for name in SKILL_NAMES:
            with self.subTest(skill=name):
                canonical = ROOT / ".agents" / "skills" / name / "SKILL.md"
                adapter = ROOT / ".claude" / "skills" / name / "SKILL.md"

                self.assertTrue(canonical.is_file())
                self.assertTrue(adapter.is_file())
                adapter_text = adapter.read_text(encoding="utf-8")
                self.assertIn(f"../../../.agents/skills/{name}/SKILL.md", adapter_text)

    def test_repo_wide_scope_and_frontend_harness_handoff_are_explicit(self):
        agents_md = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        claude_md = (ROOT / "CLAUDE.md").read_text(encoding="utf-8")

        self.assertIn("저장소 전역과 BE·FE 작업에 공통", agents_md)
        self.assertIn("Issue #167", agents_md)
        self.assertIn("Issue #165", agents_md)
        self.assertIn("frontend/CLAUDE.md", claude_md)

    def test_test_mode_never_grants_remote_write_authority(self):
        agents_md = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        canonical = (
            ROOT / ".agents" / "skills" / "knot-issue-planning" / "SKILL.md"
        ).read_text(encoding="utf-8")
        contract = (
            ROOT
            / ".agents"
            / "skills"
            / "knot-issue-planning"
            / "references"
            / "issue-contract.md"
        ).read_text(encoding="utf-8")

        for text in (agents_md, canonical, contract):
            with self.subTest(document=text[:40]):
                self.assertIn("remote_write_authorized", text)
                self.assertIn("false", text)
        self.assertIn("requested_action=publish_issue", canonical)

    def test_temporary_snapshot_is_restricted_and_removed(self):
        agents_md = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        canonical = (
            ROOT / ".agents" / "skills" / "knot-issue-planning" / "SKILL.md"
        ).read_text(encoding="utf-8")

        for text in (agents_md, canonical):
            with self.subTest(document=text[:40]):
                self.assertIn("OS 임시", text)
                self.assertIn("삭제", text)


if __name__ == "__main__":
    unittest.main()
