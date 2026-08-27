# Code-quality review: PR #187

- Target: `main...4426c19dcb763f10900fa73666e0f40a7655532d` (`be/feature/#169`)
- Linked issue: #169, Workspace / WorkspaceMember domain and persistence structure
- Verdict: **FAIL**
- Confidence: **High (0.93)**
- codeQualityStatus: **BLOCK**
- recommendation: **REQUEST_CHANGES**

## Evidence inspected

- Independently calculated target diff: 51 files total, 18 under `backend/`, 33 unrelated to the stated workspace/membership feature.
- Executed in an isolated detached worktree at `4426c19`:
  `./gradlew spotlessCheck check --no-daemon` — **passed** in 13 seconds.
  This ran 11 workspace unit tests, 4 Testcontainers integration tests, and 1 acceptance context test; no failures/errors/skips in their XML reports.
- Checked migration/JPA mapping startup under the integration and acceptance tasks, `git diff --check`, and actual commit ancestry. No Flyway duplicate-version problem exists at this target commit.

## Findings

### CRITICAL

None.

### HIGH (MAJOR)

1. **The PR contains 33 unrelated issue-planning/harness files and is not reviewable as Issue #169 alone.**
   - Files: `.agents/**`, `.claude/**`, `.github/workflows/issue-harness.yml`, `AGENTS.md`, `CLAUDE.md`, `docs/**`, and `harness/**` (33 paths total; no single line range because this is an accidental commit-range/scope finding).
   - Current behavior: `git diff main...4426c19` includes 51 files; the stated backend change list accounts for only 18. The unrelated files originate from the non-main ancestor commit `fdc0e98` and are part of the supplied PR range.
   - Risk: this feature PR adds 3,029 unrelated lines, including Python tooling and CI workflow behavior, without the requested feature review or matching Gradle verification. It makes regression attribution and rollback materially harder.
   - Suggested improvement: rebase/cherry-pick the three #169 commits onto current `main`, or otherwise remove the 33 unrelated paths before review.

2. **Workspace name validation invents and enforces a charset contract that the issue and schema do not establish.**
   - File: `backend/src/main/java/com/knot/backend/workspace/domain/Workspace.java:16, 62-66`
   - Current behavior: names containing digits, punctuation, or non-Hangul/non-ASCII letters (for example `Knot 2`) throw `INVALID_WORKSPACE_NAME`. The database accepts these values: its only name checks are `VARCHAR(20)`, non-null, and non-blank (`V1__create_workspaces_and_workspace_members.sql:1-7`).
   - Risk: valid workspace names are rejected by the application while being valid persistent data. This is an unrequested production parsing/validation rule and creates a split domain/database contract.
   - Suggested improvement: keep only the explicitly supported invariant (non-blank and 20-character maximum), or document and enforce the same charset contract at both domain and database layers after product approval.

### MEDIUM (MINOR)

1. **Boundary tests mirror the implementation constant instead of locking the ERD/schema limit.**
   - File: `backend/src/test/java/com/knot/backend/workspace/domain/WorkspaceTest.java:35, 69`
   - Current behavior: both maximum and over-maximum test fixtures derive their expected boundary from `Workspace.MAX_NAME_LENGTH`.
   - Risk: changing the production constant changes the test input too, so the tests do not independently protect the required `VARCHAR(20)`/20-character contract. This is implementation-mirroring test coverage and can give false confidence.
   - Suggested improvement: use an independently specified 20-character fixture and a 21-character failure fixture; retain a separate assertion only if the public constant itself is intentional API.

2. **Test method names do not follow the project’s required success/failure suffix convention.**
   - Files: `backend/src/test/java/com/knot/backend/workspace/domain/WorkspaceTest.java:16-103`; `WorkspaceMemberTest.java:16-98`; `WorkspaceRepositoryIntegrationTest.java:48-176`.
   - Current behavior: names such as `createWorkspace`, `rejectBlankName`, and `saveAndFindWorkspaceAndMember` omit the required `Success`/`Failure` suffix.
   - Risk: low behavioral risk, but it weakens consistency and test-result scanability in a convention-driven codebase.
   - Suggested improvement: rename tests to the established lower-camel-case, outcome-suffixed form while preserving their current given/when/then structure.

### LOW (NITPICK)

1. **The global formatter change is only tangentially justified by this feature.**
   - File: `backend/config/spotless/eclipse-formatter.xml:14`
   - Current behavior: the setting affects every annotated Java field in the repository, not just the new JPA entities; it also produces unrelated formatting-only edits in `ProjectException` and `KnotApplicationTests`.
   - Suggested improvement: move the formatter policy to a dedicated style change unless the team explicitly wants this global policy bundled. This is not a blocker and the target passes Spotless.

## Requirement assessment

- Workspace and WorkspaceMember domain classes, repository contracts, adapters, migration, unique membership constraint, and explicit `ON DELETE RESTRICT` for the workspace FK are present.
- JPA/Flyway validation and PostgreSQL Testcontainers save/read, unique, and workspace-FK checks executed successfully.
- The concurrency test exercises the database unique constraint and passed; no production transaction defect was found.
- The missing member FK is not flagged: this target commit has no Member table/domain, and the stated requirement only required explicit delete behavior for FKs that exist.

## Skill-perspective check

This check **ran** after loading the available `omo:remove-ai-slops` and `omo:programming` skill instructions.

- `remove-ai-slops`: violated by the unnecessary charset validation and its test, which merely locks an unrequested removal/restriction. The implementation-mirroring boundary tests are also weak coverage. No deletion-only, tautological, or prompt-prose tests were found.
- `programming`: violated by the implementation-mirroring boundary tests and by validation inside production code without an established boundary/goal contract. No untyped escape hatch, brittle prompt test, or needless repository abstraction was found; repository interfaces/adapters are justified by the issue’s explicit repository-contract requirement.

## Blocking issues

1. Remove the 33 unrelated harness/agent/docs/CI files from this PR’s `main...4426c19` range.
2. Remove or explicitly specify and align the unsupported workspace-name charset restriction.
