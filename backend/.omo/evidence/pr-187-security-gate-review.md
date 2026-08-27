# PR #187 Security Gate Review

## recommendation

**REJECT**

- Overall severity: **HIGH**
- Reviewed PR: `#187` — `[BE] 워크스페이스 / 멤버십 도메인 및 저장 구조 구현`
- Base/head: `main` <- `be/feature/#169`
- Exact reviewed head: `4426c19dcb763f10900fa73666e0f40a7655532d`
- Review mode: read-only security review; no GitHub comments, source edits, commits, or pushes performed.

## originalIntent

Implement the adopted Workspace and WorkspaceMember domain/repository and PostgreSQL storage contract. The issue explicitly describes membership as the basis for Knot's data isolation and authorization decisions. The schema must establish workspace/member relations and constraints, reject duplicate membership at the database layer, and be verified with domain tests, Testcontainers PostgreSQL FK/unique tests, full Flyway/JPA validation, Spotless, and backend CI.

## desiredOutcome

The delivered persistence layer must make it impossible to store a membership for a nonexistent workspace or member, must prevent duplicate `(workspace_id, member_id)` memberships under concurrency, and must expose domain objects that enforce the stated invariants. The exact PR commit must pass the requested backend verification scope without introducing dependency, secret, error-exposure, or unsafe-infrastructure regressions.

## userOutcomeReview

The PR correctly creates workspace and membership entities/repositories, prevents duplicate membership with a database unique constraint, restricts workspace deletion while memberships exist, constrains roles, and passes the configured Backend CI. However, it does not establish referential integrity from `workspace_members.member_id` to a member record. Because membership is intended to drive isolation and authorization, the database can contain an orphan membership for any positive member ID. If that ID is later allocated to a real account, the account can inherit access it was never granted. The shipped artifact therefore does not satisfy the stated relation/FK-security outcome.

## blockers

### SEC-DB-REL-01 — Missing member foreign key permits orphan/pre-provisioned authorization rows

- Severity: **HIGH**
- violatedCriterion: **SC-REL-FK** — "workspace/workspace_member tables and relations/constraints via Flyway" and "Testcontainers save/read plus FK/unique tests"; Issue #169 identifies membership as the basis for data isolation and authorization.
- Observation: `member_id` is merely `BIGINT NOT NULL` with `CHECK (member_id > 0)`. The migration defines a foreign key only for `workspace_id`; no foreign key binds `member_id` to the member table. The integration test checks rejection of a missing workspace reference but has no missing-member-reference case.
- evidencePointer:
  - `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/main/resources/db/migration/V1__create_workspaces_and_workspace_members.sql:12`
  - `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/main/resources/db/migration/V1__create_workspaces_and_workspace_members.sql:17`
  - `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/main/resources/db/migration/V1__create_workspaces_and_workspace_members.sql:21`
  - `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/test/java/com/knot/backend/workspace/infrastructure/WorkspaceRepositoryIntegrationTest.java:174`
- Attacker impact: Any write path with membership-creation capability can persist a membership for an arbitrary positive, nonexistent member ID. A subsequently created account receiving that ID may inherit workspace membership and its role, causing cross-workspace data disclosure or unauthorized actions. Orphan rows also make authorization decisions depend on allocation timing rather than a valid member relation.
- Remediation: Add the adopted ERD's member table/reference and an explicit named foreign key on `workspace_members(member_id)` with the required explicit `ON DELETE` action. Add a Testcontainers PostgreSQL test that attempts to persist a membership for a nonexistent member and proves the database rejects it. Keep the existing unique and workspace-FK tests.

## concreteFindings

1. **HIGH / BLOCKING — SEC-DB-REL-01:** Missing `member_id` foreign key, as detailed above.
2. **NOTE — authorization scoping:** `WorkspaceMemberRepository.findById(Long)` is globally keyed and does not itself scope lookup by workspace. This is an authorization-sensitive primitive, but no endpoint/service authorization contract is in this PR, so it is not tied to a stated success-criterion failure. Future callers must authorize the workspace/member relationship before returning or mutating data.
3. **NOTE — database/domain validation asymmetry:** The domain limits workspace names to Korean/ASCII letters and spaces, while the database only rejects blank names and relies on `VARCHAR(20)` for length. This could permit invalid names through direct SQL or a future alternate writer, but the issue requires domain invariant validation and does not explicitly require the same character policy as a DB check; it is therefore not a blocker.
4. **PASS — duplicate membership:** `UNIQUE (workspace_id, member_id)` is database-enforced and the integration suite covers sequential and concurrent duplicate attempts.
5. **PASS — workspace referential integrity:** The workspace FK is explicit and uses `ON DELETE RESTRICT`; the integration suite exercises a nonexistent workspace ID.
6. **PASS — role and basic input constraints:** Database role values are constrained to `OWNER`/`MEMBER`; IDs are validated as positive in the domain, and `member_id` is positive in the database.
7. **PASS — error/data exposure:** No new controller, serialization boundary, logging, stack-trace exposure, or secret handling is introduced. `ProjectException` uses fixed error-code messages rather than reflecting attacker-controlled input.
8. **PASS — dependency/supply chain:** The scoped PR adds no dependency or lockfile changes. The Testcontainers image remains outside the changed-file set.
9. **PASS — unsafe infrastructure:** The migration uses explicit constraints and no dynamic SQL, privileged extension, destructive drop, or unsafe network/service configuration.

## remove-ai-slops direct pass

- Production code is small and direct; no oversized module, dead code, broad catch, speculative parser/normalizer, or unnecessary production extraction was found.
- Repository adapters are pass-through abstractions, but they implement the explicitly required domain repository contracts and provide the architecture boundary requested by the issue; not slop in this scope.
- Domain tests assert observable constructor/factory behavior and are not deletion-only or tautological.
- Persistence tests use a real PostgreSQL container and exercise observable DB behavior. The concurrent duplicate test is useful adversarial coverage rather than excessive testing.
- Coverage weakness relevant to the blocker: the FK test class only proves the workspace-side FK and leaves member referential integrity absent and untested.

## programming direct pass

- Typed domain enums/exceptions and `Optional` repository results are used; production code contains no `Optional.get()`, domain record, test nested type, untyped escape hatch, or broad exception swallowing.
- Factories validate invariants and JPA no-arg constructors are protected as required.
- The four-argument membership factory is a minor maintainability trigger under the generic skill rubric, but it is a coherent domain construction contract and does not violate a security success criterion.
- The integration test catches the expected `DataIntegrityViolationException` narrowly. Resource shutdown is in `finally`.
- No security-relevant scope drift was found in the 18-file PR scope.

## reportCoverageCheck

- No executor evidence bundle, code-review report, manual QA matrix, or notepad path was supplied in the assignment.
- No PR-specific report artifacts were present in `/Users/yongtae/Desktop/knot-review-pr-187/.omo/evidence` or `.persona/workflow` at commit `4426c19`.
- Direct gate review therefore performed the required security, programming, and overfit/slop checks itself. Missing report coverage is not an independent blocker because direct inspection supports the verdict.
- GitHub's `Backend CI / verify` check for exact head `4426c19dcb763f10900fa73666e0f40a7655532d` completed successfully on 2026-08-25. The workflow runs `spotlessCheck`, unit tests, integration tests, acceptance tests, and `bootJar`.
- The green CI result does not address SEC-DB-REL-01 because neither the migration nor the tests contain a member foreign key/missing-member rejection case.

## checkedArtifactPaths

- Original issue: `https://github.com/woowacourse-teams/2026-Knot/issues/169`
- PR metadata/checks: `https://github.com/woowacourse-teams/2026-Knot/pull/187`
- Exact worktree: `/Users/yongtae/Desktop/knot-review-pr-187` at `4426c19dcb763f10900fa73666e0f40a7655532d`
- Full 18-file backend diff: `db17ada2e54bac129da2e9c3f81bc3dfc7d08b94..4426c19dcb763f10900fa73666e0f40a7655532d`
- Migration: `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/main/resources/db/migration/V1__create_workspaces_and_workspace_members.sql`
- Domain entities/repositories: `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/main/java/com/knot/backend/workspace/`
- Domain tests: `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/test/java/com/knot/backend/workspace/domain/`
- PostgreSQL integration tests: `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/test/java/com/knot/backend/workspace/infrastructure/WorkspaceRepositoryIntegrationTest.java`
- Runtime persistence configuration: `/Users/yongtae/Desktop/knot-review-pr-187/backend/src/main/resources/application.properties`
- CI workflow: `/Users/yongtae/Desktop/knot-review-pr-187/.github/workflows/backend-ci.yml`
- Required review criteria consulted: `/Users/yongtae/.codex/plugins/cache/sisyphuslabs/omo/4.19.4/skills/remove-ai-slops/SKILL.md`, `/Users/yongtae/.codex/plugins/cache/sisyphuslabs/omo/4.19.4/skills/programming/SKILL.md`

## exactEvidenceGaps

- No adopted ERD artifact/path was provided or present in the scoped PR, so the expected member-table name and exact `ON DELETE` action cannot be independently quoted. This does not erase the blocker: the issue itself requires workspace/member relations and FK tests, while the delivered schema demonstrably has no member FK.
- No PR-specific executor report, code-review report, manual QA matrix, or notepad was available.
- CI proves configured tasks passed but supplies no security scanner/SCA result; no dependency changes occurred in scope, so this is a note rather than a blocker.
- Tests were not re-run locally because the assignment is expressly read-only and Gradle execution would write build artifacts. Exact-head CI status was independently verified through GitHub.
