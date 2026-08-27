# Code-quality review — GitHub OAuth / JWT / member

## Verdict

**FAIL** — request changes.

## Scope and evidence inspected

- Uncommitted production and test changes under `auth`, `member`, `global/config`, plus `build.gradle`, properties, and `V1__create_members.sql`.
- `git diff --check`: passed.
- `./gradlew test integrationTest acceptanceTest --rerun-tasks --console=plain`: passed. Unit task executed 14 tests; acceptance task executed 4 tests; `integrationTest` executed with no tagged tests.
- No `ulw-loop` plan exists, so this report uses the required fallback location.

## Philosophy and skill-perspective check

This check ran. I loaded and applied `omo:programming` and `omo:remove-ai-slops`, and reviewed the philosophy harness `AGENTS.md`, architecture/principle/workflow guidance, all accepted decisions, and the related pending decisions.

- `programming` perspective: violated by Mockito-based service tests, a sleep-based timing test, and unneeded token-value normalization/ternary in the production JWT provider.
- `remove-ai-slops` perspective: violated by the same implementation-mirroring mock tests and by the unnecessary empty-string/null conversion used solely to compensate for a self-created claim representation. No deletion-only, prose/prompt, or pure tautological tests were found.
- Harness: violated by JPA/Spring Data in the member domain boundary, repository interface placement, direct infrastructure dependency from the service, Mockito instead of a top-level fake, missing direct tests, and forbidden ternary use.
- Project-profile conflict: `.persona/project-profile.jsonc` describes Spring Data JPA/Mockito and says a port is not default for a single implementation. That conflicts with the harness requirements for a technology-free domain, a domain repository interface, and service fakes. Under the harness's stated priority, accepted decisions govern this review; the conflict needs an explicit project decision before implementation proceeds.

## Findings

### CRITICAL

None.

### HIGH

1. **The member domain depends directly on persistence technology, and the repository abstraction is inverted.** `Member` imports and uses JPA annotations at `src/main/java/com/knot/backend/member/domain/Member.java:4-21`; `MemberRepository` is an infrastructure package interface extending Spring Data at `src/main/java/com/knot/backend/member/infrastructure/MemberRepository.java:1-9`; `MemberService` therefore imports the infrastructure implementation at `src/main/java/com/knot/backend/member/application/MemberService.java:7,16`. This violates the requested domain purity and the accepted `domain-does-not-know-technology` and `repository-interface-in-domain` decisions. It leaves the application service coupled to Spring Data and makes a persistence substitution impossible without changing application code.
   - Minimal refactoring: make a technology-neutral `member.domain.MemberRepository` with only the operations the use case needs; implement it through a Spring Data/JPA adapter in `member.infrastructure`; remove JPA annotations from the domain model (or explicitly change the governing architecture decision before retaining the integrated entity approach).

2. **The central OAuth completion path lacks direct or end-to-end behavior coverage.** `GithubOAuth2UserService` (`src/main/java/com/knot/backend/auth/infrastructure/github/GithubOAuth2UserService.java:22-43`), `GithubOAuth2User` (`.../GithubOAuth2User.java:18-45`), `OAuth2AuthenticationSuccessHandler` (`src/main/java/com/knot/backend/auth/presentation/OAuth2AuthenticationSuccessHandler.java:31-64`), and `OAuth2AuthenticationFailureHandler` (`.../OAuth2AuthenticationFailureHandler.java:14-20`) have no direct tests. The sole OAuth acceptance test only verifies that Spring generates the provider authorization URL (`src/test/java/com/knot/backend/KnotApplicationTests.java:37-45`); it never exercises a GitHub callback, member upsert, cookie emission, redirect, malformed provider attributes, or failure redirect. This falls short of the explicit direct-test requirement and offers false confidence for the feature's primary behavior.
   - Minimal refactoring: add focused direct tests for each listed class and one callback-level test that proves valid GitHub attributes create/update a member, emits the configured HttpOnly cookie, and redirects; cover malformed attributes and authentication failure separately.

3. **Service tests use Mockito against the Spring Data repository rather than a domain fake.** `src/test/java/com/knot/backend/member/application/MemberServiceTest.java:5-9,26-31,46-51,67-75` uses mocks, stubbing, interaction verification, and `any(...)`. This violates the harness fake-over-mock rule and makes the tests sensitive to `MemberService`'s current implementation sequence rather than only its observable repository effect. It also entrenches the production-boundary inversion above.
   - Minimal refactoring: test against a standalone `src/test/java/com/knot/backend/member/fake/FakeMemberRepository.java` that implements the domain repository; assert saved/found members through fake state rather than Mockito interactions.

### MEDIUM

1. **JWT claim serialization adds unnecessary normalization and violates the explicit no-ternary rule.** `src/main/java/com/knot/backend/auth/infrastructure/jwt/JwtProvider.java:72-76,140-141` writes an absent profile image as `""` and immediately normalizes blank claims back to `null`. This extra representation exists only within the provider and is not required by the JWT boundary. It is also the only detected forbidden ternary.
   - Minimal refactoring: preserve `null` directly as the optional claim value (or omit the claim) and remove `emptyToNull`; use an ordinary conditional only if the chosen JWT API requires one.

2. **The expiry test is timing-based and can flake.** `src/test/java/com/knot/backend/auth/infrastructure/jwt/JwtProviderTest.java:34-40` relies on a 10-ms expiration plus `Thread.sleep(50)`. Scheduler delays and JWT timestamp precision make this test environment-dependent; it also conflicts with the programming skill's deterministic-test rule.
   - Minimal refactoring: inject a `Clock`/time source into `JwtProvider` and advance it deterministically in the test, or construct a signed expired token with a fixed timestamp.

3. **The find-or-create flow has an unhandled concurrent-first-login race.** `src/main/java/com/knot/backend/member/application/MemberService.java:24-31` performs read-then-insert. Two first logins for the same GitHub ID can both observe absence; the unique constraint in `src/main/resources/db/migration/V1__create_member.sql:7` then rejects one request. No test documents or handles this expected race.
   - Minimal refactoring: choose and test a single policy: catch the unique-constraint conflict and re-read the member, or use a database-native upsert behind the infrastructure adapter.

4. **Domain tests cover only selected invalid inputs and omit the public successful behavior and remaining invariants.** `AuthenticatedMemberTest` has only one invalid-ID test (`src/test/java/com/knot/backend/auth/domain/AuthenticatedMemberTest.java:11-20`); `OAuthUserTest` checks only invalid ID/blank nickname (`.../OAuthUserTest.java:11-26`); `MemberTest` has only error cases (`src/test/java/com/knot/backend/member/domain/MemberTest.java:11-29`). Tests do not lock successful construction, profile updates, invalid GitHub ID, excessive nickname, or blank profile URL.
   - Minimal refactoring: add direct, behavior-level tests for each factory/mutator invariant and at least one successful state assertion per domain class.

### LOW

1. **The fallback redirect is a hard-coded presentation rule with no configuration or direct test.** `src/main/java/com/knot/backend/auth/presentation/OAuth2AuthenticationFailureHandler.java:20` always redirects to `/login?error=oauth2`; unlike the success redirect, it cannot be adjusted by deployment configuration.
   - Minimal refactoring: either keep it as a documented fixed route and test it directly, or make failure redirect configuration part of the same OAuth login properties if deployment-specific behavior is required.

2. **The OAuth service is marked as a generic Spring `@Service` despite being a GitHub infrastructure adapter.** `src/main/java/com/knot/backend/auth/infrastructure/github/GithubOAuth2UserService.java:15-17` is technically correct but obscures the adapter role and does not need application-service semantics.
   - Minimal refactoring: use `@Component` (or expose a configuration bean) to make the infrastructure role clearer; this is non-blocking.

## Recommendation

- `codeQualityStatus`: **BLOCK**
- `recommendation`: **REQUEST_CHANGES**
- `blockers`: the HIGH findings above — domain/repository inversion, missing OAuth-completion coverage, and Mockito service tests instead of a top-level fake repository.
