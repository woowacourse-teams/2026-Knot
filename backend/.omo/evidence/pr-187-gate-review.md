# PR #187 Gate Review — commit `4426c19`

## recommendation

**REJECT**

The pinned artifact compiles and all executable backend gates pass, but it does not exactly implement the adopted schema relation contract and it violates an explicit test-naming constraint.

## originalIntent

Issue #169 asks for the Workspace and WorkspaceMember domain/repository foundation from the adopted ERD, Flyway tables and relations/constraints, database-enforced duplicate-membership prevention, domain invariant tests, and PostgreSQL persistence/FK/unique tests. The requested review additionally fixes the scope to PR #187 at `4426c19` and requires compliance with the supplied project conventions.

## desiredOutcome

A merge-ready Java 25/Spring Boot 4.1 change in which:

1. `Workspace` and `WorkspaceMember` protect their stated invariants and expose domain repository contracts.
2. Flyway creates the ERD-contracted `workspace` and `workspace_member` structures with both referenced relations, named constraints, and explicit `ON DELETE` policies.
3. `(workspace_id, member_id)` is unique at the database level, including concurrent writes.
4. Testcontainers PostgreSQL applies all Flyway migrations, Hibernate validates mappings with `ddl-auto=validate`, and save/read, FK, unique, domain normal/error/boundary, Spotless, unit, integration, acceptance, and build gates pass.
5. Tests follow given/when/then and the required English lowerCamelCase behavior name plus `_success` / `_failure_<cause>` suffix convention, with no nested test types.

## goalBreakdown

| Criterion | Result | Evidence |
| --- | --- | --- |
| C1 Workspace/WorkspaceMember domain and repository contracts | PASS | `Workspace.java`, `WorkspaceMember.java`, both domain repository interfaces, JPA repositories, and adapters at `4426c19` |
| C2 Adopted ERD tables and relations/constraints via Flyway | FAIL | Migration lines 1 and 9 use plural names; lines 11–22 define only the workspace FK and no member FK |
| C3 DB-enforced duplicate membership | PASS | Migration line 16 unique constraint; sequential and concurrent integration tests at lines 93–172 pass |
| C4 Domain invariant normal/exception/boundary tests | PASS with NOTE | 11 unit tests pass; max-length/over-length, blank/special character, zero IDs, and null role/time classes are exercised. Null IDs and negative IDs are not separately exercised, but the stated implementation predicates cover them and the Issue does not enumerate each case |
| C5 PostgreSQL persistence and FK/unique integration tests | FAIL | Save/read, workspace FK, and unique tests pass, but no member FK exists and therefore no member-FK test exists |
| C6 Full Flyway + JPA mapping validation | PASS for the implemented schema | `application.properties` sets Flyway enabled and `spring.jpa.hibernate.ddl-auto=validate`; isolated Testcontainers run passes |
| C7 Spotless and backend CI scope | PASS | Isolated pinned run completed `spotlessCheck test integrationTest acceptanceTest bootJar --rerun-tasks` successfully |
| C8 Java/JPA/domain conventions | PASS with NOTE | Java 25/SB 4.1/Gradle/PostgreSQL/JPA/Flyway; protected no-arg constructors, static factories, constructor invariant checks; no domain record or nested test types; no production `Optional.get()` |
| C9 Test naming convention | FAIL | All 16 changed test methods omit the mandatory success/failure suffix; representative lines: `WorkspaceTest.java:16,33,49`, `WorkspaceMemberTest.java:16,38,58`, `WorkspaceRepositoryIntegrationTest.java:48,95,128,176`, `KnotApplicationTests.java:23` |
| C10 Every FK declares explicit ON DELETE | PASS for the only declared FK, but incomplete under C2/C5 | Workspace FK has `ON DELETE RESTRICT` at migration line 20; the missing member FK cannot satisfy the relation requirement |

## blockers

### B1 — Adopted table-name contract is not implemented

- **violatedCriterion:** C2 — create the adopted ERD `workspace` / `workspace_member` tables and relations/constraints through Flyway.
- **observation:** The migration creates `workspaces` and `workspace_members`, and the JPA `@Table` mappings use the same plural names. The supplied project rule says an explicitly named external ERD/schema contract overrides the plural-table default.
- **evidencePointer:** `/tmp/knot-pr187-review.yhFZiv/src/main/resources/db/migration/V1__create_workspaces_and_workspace_members.sql:1`, `:9`; `Workspace.java` `@Table(name = "workspaces")`; `WorkspaceMember.java` `@Table(name = "workspace_members")`; supplied project guideline “Naming과 key”.

### B2 — WorkspaceMember-to-Member relation and required FK coverage are absent

- **violatedCriterion:** C2 and C5 — implement table relations/constraints and verify FK constraints on Testcontainers PostgreSQL.
- **observation:** `member_id` is only `BIGINT NOT NULL` plus a positive check. There is no `FOREIGN KEY (member_id) REFERENCES member... ON DELETE ...`, and the integration suite tests only a missing workspace reference. The PR body explicitly acknowledges that the member FK was omitted pending PR #183; that is a dependency note, not completion of Issue #169.
- **evidencePointer:** `/tmp/knot-pr187-review.yhFZiv/src/main/resources/db/migration/V1__create_workspaces_and_workspace_members.sql:12`, `:17-22`; `/tmp/knot-pr187-review.yhFZiv/src/test/java/com/knot/backend/workspace/infrastructure/WorkspaceRepositoryIntegrationTest.java:174-190`; PR #187 body “`workspace_members.member_id` FK는 포함하지 않았습니다.”

### B3 — Changed tests violate the mandatory success/failure naming contract

- **violatedCriterion:** C9 — English lowerCamelCase behavior names with `_success` / `_failure_<cause>` suffixes.
- **observation:** The changed tests use names such as `createWorkspace`, `rejectBlankName`, `saveAndFindWorkspaceAndMember`, and `rejectMissingWorkspaceReference`; none includes the required suffix.
- **evidencePointer:** `/tmp/knot-pr187-review.yhFZiv/src/test/java/com/knot/backend/workspace/domain/WorkspaceTest.java:16-103`; `/tmp/knot-pr187-review.yhFZiv/src/test/java/com/knot/backend/workspace/domain/WorkspaceMemberTest.java:16-98`; `/tmp/knot-pr187-review.yhFZiv/src/test/java/com/knot/backend/workspace/infrastructure/WorkspaceRepositoryIntegrationTest.java:48-176`; supplied development guideline “Test naming”.

## representativeFlows

1. **Create and reload:** valid Workspace is saved, valid OWNER membership is saved, persistence context is cleared, both are read through domain repository contracts, and membership existence lookup returns true — PASS.
2. **Duplicate membership:** a second identical `(workspace_id, member_id)` insert raises `DataIntegrityViolationException`; two concurrent inserts yield exactly one success and one failure — PASS in reproduced PostgreSQL run.
3. **Reference integrity:** membership with a nonexistent workspace fails — PASS; membership with a nonexistent member is accepted because no member FK exists — FAIL against C2/C5.

## edgeCasesReviewed

1. Workspace name length exactly 20 — accepted and tested.
2. Workspace name length 21 — rejected and tested.
3. Blank and special-character workspace names — rejected and tested; null name follows the same production guard but has no dedicated test.
4. Workspace/member ID at zero — rejected and tested; negative and null IDs follow the same guard but lack dedicated tests.
5. Null role, `createdAt`, and `joinedAt` — rejected and tested for role/timestamps.
6. Duplicate membership under sequential inserts — rejected by the unique constraint.
7. Duplicate membership under concurrent inserts — exactly one insert succeeds in the reproduced run.
8. Missing workspace reference — rejected by FK.
9. Missing member reference — not rejected because the relation is absent.
10. Role outside `OWNER`/`MEMBER` and blank DB workspace name — database CHECK constraints exist; no direct integration test, which is a NOTE because the Issue specifically names FK/unique tests rather than every CHECK.

## userOutcomeReview

From the user’s perspective, the PR provides a working partial foundation: domain creation, repository save/read, workspace FK enforcement, uniqueness under concurrency, and full executable CI all work. It is not the exact ERD-backed foundation requested because one of WorkspaceMember’s defining relations is deferred and the explicitly supplied table names are changed. A later merge of PR #183 would also create a Flyway `V1` collision, as the PR body acknowledges; therefore this commit is not independently merge-ready against the intended combined schema.

## removeAiSlopsAndProgrammingPass

Direct pass performed over all 18 changed files:

- No deletion-only, requested-removal, prose-pin, snapshot, tautological, or mock-return tests were added.
- Domain tests assert observable values/error codes; integration tests exercise real PostgreSQL and real constraints rather than mirror repository implementation.
- The concurrent duplicate test is beyond the minimum Issue wording but materially proves the database uniqueness requirement under the stated concurrency invariant; it is not useless overfit.
- `saveAndFlush` overloads and `saveAndReload` are test-only helpers used to force database effects and detach JPA state; they are justified, not needless production extraction.
- Repository adapters are pass-through wrappers, but they implement the explicitly requested domain repository boundary and preserve domain-first layering; NOTE, not slop blocker.
- No oversized production module, dead code, broad catch, unnecessary parsing/normalization, performance anti-pattern, or speculative production abstraction was found.
- Missing member-FK behavior coverage is substantive under C2/C5 and is captured as B2.

No PR-specific code-review report, executor report, manual QA matrix, or notepad artifact was supplied or found. Therefore there is no report artifact demonstrating its own programming/remove-ai-slops coverage. This does not independently block approval because the direct pass was completed; it is an evidence gap recorded below.

## checkedArtifactPaths

- Git commit: `4426c19dcb763f10900fa73666e0f40a7655532d`
- PR metadata: GitHub PR #187, head `be/feature/#169`, base `main`, 18 backend files
- Issue metadata: GitHub Issue #169 body and TODO list
- Isolated pinned artifact: `/tmp/knot-pr187-review.yhFZiv`
- Migration: `/tmp/knot-pr187-review.yhFZiv/src/main/resources/db/migration/V1__create_workspaces_and_workspace_members.sql`
- Production domain/repository code under `/tmp/knot-pr187-review.yhFZiv/src/main/java/com/knot/backend/workspace/`
- Tests under `/tmp/knot-pr187-review.yhFZiv/src/test/java/com/knot/backend/workspace/` and `KnotApplicationTests.java`
- Runtime config: `/tmp/knot-pr187-review.yhFZiv/src/main/resources/application.properties`
- Build/CI config: `/tmp/knot-pr187-review.yhFZiv/build.gradle` and `.github/workflows/backend-ci.yml` at the pinned commit
- Reproduced XML results under `/tmp/knot-pr187-review.yhFZiv/build/test-results/`
- Project constraint source: `.persona/development-guideline.md` supplied/current workspace, especially PostgreSQL/JPA/Flyway and Test naming sections
- Required skills consulted directly: `omo:programming`, `omo:remove-ai-slops`

## reproducedEvidence

Command run in the isolated archive of `4426c19`:

```text
./gradlew spotlessCheck test integrationTest acceptanceTest bootJar --rerun-tasks
BUILD SUCCESSFUL in 10s
10 actionable tasks: 10 executed
```

Test result counts:

- Unit: 11 passed, 0 failed (`WorkspaceTest` 6; `WorkspaceMemberTest` 5)
- Integration: 4 passed, 0 failed (Testcontainers PostgreSQL)
- Acceptance: 1 passed, 0 failed
- Spotless: passed
- Flyway/JPA validation: application uses full migration location and `ddl-auto=validate`; Testcontainers context passed
- Boot jar: passed
- GitHub PR check `verify`: passed, but local reproduction above is the approval evidence

## exactEvidenceGaps

1. No adopted ERD artifact was included in the review packet or discoverable in Issue #169 comments; table-name and member-relation expectations are therefore grounded in the user-supplied requirement text, the Issue text, the PR’s own omission note, and the supplied project guideline.
2. No PR-specific executor evidence, code-review report, manual QA matrix, or notepad path was supplied/found.
3. No integration test can demonstrate member FK enforcement because the FK itself is absent.
4. The unit suite does not separately test null/negative IDs or null workspace name; noted but not blocking because the named criterion does not enumerate those partitions and the guards are directly inspectable.

## notes

- The migration uses `GENERATED BY DEFAULT AS IDENTITY` at lines 2 and 10 while the supplied project guideline states `GENERATED ALWAYS AS IDENTITY` as the default. This is a convention deviation, but without the adopted ERD artifact it is recorded as a NOTE rather than an additional blocker.
- AssertJ `OptionalAssert.get()` is used in integration assertions; this is not a direct Java `Optional.get()` call. The production reload helper uses `orElseThrow()`, so the explicit `Optional.get()` prohibition is not treated as violated.
- The PR changes the global formatter and reformats two unrelated existing field declarations. This is minor scope expansion, but it does not violate a stated Issue success criterion and is therefore a NOTE only.
