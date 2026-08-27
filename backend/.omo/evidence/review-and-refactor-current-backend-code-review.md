# Code Quality Review — GitHub OAuth/JWT/member slice

## Verdict

- `codeQualityStatus`: BLOCK
- `recommendation`: REQUEST_CHANGES

## Scope and evidence inspected

The ULW loop has no active plan (`omo ulw-loop status --json` returned
`ULW_LOOP_PLAN_MISSING`), so this artifact uses the required fallback location.

Reviewed the current uncommitted OAuth/JWT/member implementation, including all
untracked Java sources and tests, rather than relying only on `git diff` (which
lists only four tracked files). Inspected the philosophy harness `AGENTS.md`,
related architecture/principle/workflow documents, every accepted decision, and
every pending decision; `.persona/project-profile.jsonc` and its guideline; and
history for the touched paths. The nearest commits are `d9a452d` (Security and
OAuth client dependencies) and `1f7c653` (GitHub OAuth properties). The rest of
this feature is uncommitted and therefore has no historical commit rationale.

Validation performed on 2026-08-24:

- `./gradlew test --rerun-tasks --no-daemon`: PASS
- `./gradlew integrationTest --rerun-tasks --no-daemon`: PASS
- `./gradlew acceptanceTest --rerun-tasks --no-daemon`: PASS
- `./gradlew spotlessCheck --rerun-tasks --no-daemon`: PASS
- `git diff --check`: PASS

An initial parallel Gradle rerun produced class-file races in `compileTestJava`.
That evidence was discarded; the sequential runs above are the valid result.

## Skill-perspective check

Ran: yes. Loaded and applied `omo:remove-ai-slops` and `omo:programming` before
judging tests and maintainability. The programming skill's Java-specific
toolchain guidance is unavailable, so it was applied only as the requested
cross-language design lens (clear boundaries, no needless parsing/validation,
tests that prove behaviour rather than implementation).

- `remove-ai-slops`: violated by the Mockito service tests and the sleep-based
  expiry test; no deletion-only, prose/prompt, or implementation-constant test
  was found. Production parsing in `GithubUserAttributes` is necessary at the
  external-provider boundary. JWT claim parsing is also required at its trust
  boundary, but the optional profile-image normalization is avoidable scope
  growth.
- `programming`: violated by implementation-mirroring Mockito verification,
  the brittle wall-clock test, and production validation/normalization that
  should be minimized at boundaries. No untyped Java escape hatch was found;
  `Map<String, Object>` stays inside the Spring OAuth adapter.

## Findings

### CRITICAL

None.

### HIGH

1. The member persistence boundary directly contradicts the accepted repository
   decision and prevents the required fake-based service test.

   - `src/main/java/com/knot/backend/member/application/MemberService.java:7,16`
     imports and depends on `member.infrastructure.MemberRepository`.
   - `src/main/java/com/knot/backend/member/infrastructure/MemberRepository.java:5,7`
     exposes `JpaRepository` as the only repository contract.

   The philosophy harness requires a domain-level persistence interface with an
   infrastructure implementation. This is not merely package naming: the
   application service is coupled to Spring Data and the domain has no storage
   requirement of its own. Refactor now, minimally: introduce a domain
   `MemberRepository` limited to the required operations, retain a Spring Data
   adapter/implementation in `member.infrastructure`, and inject the domain
   interface into the service.

2. `MemberServiceTest` uses Mockito where the accepted decision explicitly
   requires a separately located fake repository.

   - `src/test/java/com/knot/backend/member/application/MemberServiceTest.java:5-9,26-31,46-51,67-75`

   The mocks and `verify` assertions mirror `save`/lookup mechanics rather than
   proving the service workflow against state. This creates false confidence and
   directly conflicts with the harness. Refactor now, together with finding 1:
   add an external `src/test/java/com/knot/backend/member/domain/fake/`
   implementation and assert its observable stored state. Do not use a nested
   fake.

### MEDIUM

1. The code violates the explicit project constraint against ternaries.

   - `src/main/java/com/knot/backend/auth/infrastructure/jwt/JwtProvider.java:74-76`
   - `src/main/java/com/knot/backend/auth/infrastructure/jwt/JwtProvider.java:141`

   Refactor now within the JWT boundary; use named branching that preserves
   claims semantics. This is a policy/maintainability issue, not shown to cause
   an observed runtime defect.

2. The JWT expiry test is wall-clock based and can flake under scheduling load.

   - `src/test/java/com/knot/backend/auth/infrastructure/jwt/JwtProviderTest.java:34-47`

   `Thread.sleep(50)` is forbidden by the slop perspective and does not prove
   deterministic expiry behaviour. Refactor now with an injected `Clock` or a
   decoder/validator seam that allows an explicit time. Keep the test focused on
   observable rejected authentication.

3. Direct public-behaviour coverage is incomplete for several non-trivial
   production classes: `GithubOAuth2User`, `GithubOAuth2UserService`,
   `AuthController`, OAuth success/failure handlers, and both configuration
   property classes lack direct tests. The acceptance test covers only the
   authorization redirect and JWT-cookie `/auth/me` path; it does not exercise
   the provider-to-member-to-cookie success handler or failure redirect.

   - `src/main/java/com/knot/backend/auth/infrastructure/github/GithubOAuth2User.java`
   - `src/main/java/com/knot/backend/auth/infrastructure/github/GithubOAuth2UserService.java`
   - `src/main/java/com/knot/backend/auth/presentation/AuthController.java`
   - `src/main/java/com/knot/backend/auth/presentation/OAuth2AuthenticationSuccessHandler.java`
   - `src/main/java/com/knot/backend/auth/presentation/OAuth2AuthenticationFailureHandler.java`

   Add narrow direct tests only for the behaviour-bearing classes/handlers;
   configuration holders may be excluded if explicitly documented as bootstrap
   DTOs. Avoid tests that assert internal constants or Spring configuration
   wiring text.

4. `JwtProvider` normalizes a missing/blank `profile_image_url` claim to an
   empty string during issuance and back to `null` during authentication.

   - `src/main/java/com/knot/backend/auth/infrastructure/jwt/JwtProvider.java:72-76,140-142`

   This is unnecessary data normalization beyond the OAuth/JWT goal and is only
   indirectly tested. Defer unless compatibility requires it; if retained,
   document and test the claimed API contract. Do not broaden the refactor.

### LOW

1. `Member` is a JPA-annotated domain class.

   - `src/main/java/com/knot/backend/member/domain/Member.java:4-21`

   The philosophy harness says the domain should not know JPA, but the pending
   `domain-entity-separation` decision intentionally permits a unified model
   only when persistence conversion keeps the domain technology-free. The
   project profile's explicit current convention describes Spring Data JPA and
   simple-layered architecture. Given the harness priority (current production
   code first; then accepted decisions) and lack of an existing member model,
   the documents conflict. Defer entity/domain separation; resolve it as a
   documented project decision before expanding the model, not in this OAuth
   slice.

2. Prior workflow reports claim broad approval and “15 unit tests,” but they
   omit artifact paths and do not reflect the full untracked worktree in Git
   diff. They are not accepted as approval evidence. The current report and
   commands above are the review evidence.

## Philosophy/profile conflict decisions

| Issue | Decision | Rationale and minimal scope |
| --- | --- | --- |
| Domain-first packages | keep | Both profile and harness agree; the auth/member top-level layout follows it. |
| Domain repository port + infrastructure adapter | refactor now | Accepted harness rule, required for domain independence and the mandated fake test. The profile's “no port for a single external implementation” conflicts, but it is lower priority than the harness's accepted repository decision for this task. |
| JPA annotations on `Member` | defer | Pending decision/profile favour simple JPA; accepted architecture wants a technology-free domain. A full split is broader than the current defect and not needed to introduce the repository boundary. |
| Service orchestration | keep after port refactor | `findOrCreate` is otherwise a compact transaction-scoped orchestration flow; domain validation remains in `Member`. |
| HTTP request validation | keep | No request DTO exists in this GET/OAuth callback slice. Provider-map parsing belongs at the external OAuth boundary, not in the domain. |
| OAuth state lifecycle | keep | Spring Security owns it, matching the profile. |
| Mockito and fake location | refactor now | Both the accepted fake decision and test package guidance require an external fake. |
| Ternaries and sleep-based expiry test | refactor now | Explicit task constraints and slop/programming review reject both; scope is limited to JWT code/tests. |
| Full entity/domain separation, aggregate/event boundaries, validator package | defer | These are pending decisions or lack a concrete trigger in this single-member login flow. |

## Required blockers before approval

1. Move the member repository contract to `member.domain`, create an
   infrastructure adapter/implementation, and make `MemberService` depend only
   on the domain contract.
2. Replace Mockito in `MemberServiceTest` with a standalone fake in the required
   test fake package; assert behaviour/state rather than interactions.

The MEDIUM issues should be included in the same bounded refactor, but the two
HIGH findings alone require `REQUEST_CHANGES`.
