# Lane 4 Security Gate Review

## recommendation

REJECT

## originalIntent

Review only, without changing or committing product files, the uncommitted GitHub OAuth2, JWT-cookie authentication, and member persistence slice against the supplied philosophy harness. Lane 4 specifically requires auditing login, provider mapping, JWT issuance/validation/filtering, cookie transport, redirects, authentication entry points, errors, secrets/configuration, and member upsert behavior.

## desiredOutcome

A production-service authentication slice that does not expose credentials or permit authentication bypass, preserves the intended GitHub login and `/auth/me` behavior, and has evidence proportionate to its security-sensitive paths.

## userOutcomeReview

The implementation uses Spring Security OAuth state handling, validates GitHub's required `id` and `login`, signs HS256 JWTs with a minimum 32-byte configured secret, validates signature and expiration through Nimbus, stores the JWT in an HttpOnly/SameSite=Lax cookie, and rejects unauthenticated protected requests. However, the bearer-token cookie is insecure by default: both the bound property and repository configuration default `Secure` to false, and no checked artifact establishes an HTTPS-only compensating control. A default production deployment can therefore send the authentication credential over plaintext HTTP. This directly fails the requested credential-exposure security criterion.

## blockers

### CRITICAL — SEC-COOKIE-TRANSPORT

- **violatedCriterion:** Any issue that would expose credentials is critical; audit cookie settings and secrets/configuration for the production-service authentication slice.
- **observation:** The JWT bearer cookie is created using `secure(jwtProperties.isSecure())`, while `JwtProperties.secure` defaults to false and `application.properties` uses `${JWT_COOKIE_SECURE:false}`. No repository deployment or server configuration checked here proves HTTP is rejected or redirected before cookies can be transmitted. Consequently, omission or typo of the environment variable produces a non-Secure authentication cookie.
- **evidencePointer:** `src/main/java/com/knot/backend/auth/presentation/OAuth2AuthenticationSuccessHandler.java:39-45`; `src/main/java/com/knot/backend/global/config/JwtProperties.java:13-16`; `src/main/resources/application.properties:28`; `.persona/project-profile.jsonc` (`project-goal: production-service`).
- **remediation:** Make secure transport fail closed: default `auth.jwt.secure=true` (and remove the false fallback), validate production configuration at startup, and permit `false` only in an explicit local/test profile. Add an HTTP-level assertion that the OAuth success `Set-Cookie` contains `Secure`, `HttpOnly`, `SameSite=Lax`, `Path=/`, and the intended lifetime.

## nonBlockingFindings

### HIGH — session authentication remains enabled

`SecurityConfig` does not declare a session policy or security-context repository. Spring OAuth login may therefore retain the authenticated security context in the HTTP session in addition to issuing the JWT. This is not proven to violate a stated criterion or bypass authentication, so it is a NOTE, but it makes the actual authentication model and logout/revocation behavior ambiguous. Decide explicitly whether the application is JWT-only after callback; if so, use an explicit stateless/no-session persistence design while preserving OAuth authorization-request state during the handshake.

Evidence: `src/main/java/com/knot/backend/global/config/SecurityConfig.java:29-56`.

### MEDIUM — first-login persistence is not an atomic upsert

`findByGithubId` followed by `save` races for simultaneous first logins. The unique DB constraint prevents duplicate identities, so this is not an authentication bypass, but one request can fail with a server error. Handle the unique-conflict race by re-reading the existing member in a bounded transaction/retry policy.

Evidence: `src/main/java/com/knot/backend/member/application/MemberService.java:18-31`; `src/main/resources/db/migration/V1__create_member.sql:2-7`.

### MEDIUM — redirect target is unrestricted configuration

`AUTH_SUCCESS_REDIRECT_URI` is passed directly to `sendRedirect`. This is not attacker-controlled in the checked code, so it is not a demonstrated open redirect, but a compromised or mistaken environment value can redirect with the newly issued cookie flow to an unintended origin. Bind this to a validated relative path or an allowlisted origin.

Evidence: `src/main/java/com/knot/backend/auth/presentation/OAuth2AuthenticationSuccessHandler.java:46-47`; `src/main/resources/application.properties:31`.

## philosophyAndSlopReview

The supplied philosophy harness takes precedence over pending guidance and conflicts with the Persona project profile in two explicit areas: the accepted harness requires a repository interface in `member/domain` with an infrastructure implementation and fake repositories for service tests; the Persona profile/plan prefers no interface for one implementation and permits Mockito. The current artifact follows Persona, not the accepted philosophy: `MemberRepository` is a Spring Data interface in infrastructure and `MemberServiceTest` uses Mockito. These are outside the Lane 4 security verdict but are user-requested review findings.

The direct remove-ai-slops/programming pass found no oversized production file, generic util/manager/wrapper, deletion-only test, or tautological removal test. It did find narrow/implementation-coupled assurance: the JWT round-trip test uses the same provider for issue and verify, no adversarial tampered-signature/wrong-key test exists, the expiry test uses `Thread.sleep`, and security-sensitive production classes/handlers lack direct tests. The review report does not explicitly document remove-ai-slops overfit categories or the programming-skill perspective, so its approval does not establish that coverage.

Evidence: `src/test/java/com/knot/backend/auth/infrastructure/jwt/JwtProviderTest.java:16-47`; `src/test/java/com/knot/backend/member/application/MemberServiceTest.java:1-76`; `.persona/workflow/review-report.md`.

## checkedArtifacts

- `/Users/yongtae/Documents/하네스/철학 하네스/AGENTS.md`
- Accepted decisions: domain technology independence, repository interface in domain, fake over mock, plus pending security-auth and fake-location decisions
- `.persona/project-profile.jsonc`
- `.persona/workflow/plan.md`
- `.persona/workflow/implementation-report.md`
- `.persona/workflow/review-report.md`
- Actual Git status/diff and all scoped production/tests under `auth`, `member`, `global/config`, `global/exception`
- `build.gradle`
- `src/main/resources/application.properties`
- `../../src/main/resources/db/migration/V1__create_members.sql`
- `./gradlew test --no-daemon` reproduced on 2026-08-24: BUILD SUCCESSFUL
- `omo ulw-loop status --json`: no ULW plan, so this fallback evidence path is used

## exactEvidenceGaps

- No manual QA matrix or notepad path was supplied.
- No live GitHub consent/callback evidence exists; both workflow reports acknowledge this.
- No deployment artifact proves HTTPS-only transport or guarantees `JWT_COOKIE_SECURE=true`.
- No direct cookie-attribute test covers the OAuth success handler.
- No adversarial JWT test independently verifies tampered signatures, wrong keys, malformed claims, or future-issued tokens.
- No direct tests were found for `GithubOAuth2UserService`, `GithubOAuth2User`, OAuth success/failure handlers, `AuthController`, `SecurityConfig`, or configuration-property validation.
- The code review report lacks explicit remove-ai-slops overfit/slop criterion coverage and explicit programming-skill coverage.
