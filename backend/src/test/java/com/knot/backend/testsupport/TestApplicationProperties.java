package com.knot.backend.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {"auth.jwt.secret=test-jwt-secret-012345678901234567890123456789",
        "auth.jwt.secure=false", "auth.jwt.cookie-name=KNOT_ACCESS_TOKEN",
        "workspace.invitation.lookup-hash-key=ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA",
        "workspace.invitation.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY",
        "notion.oauth.client-id=test-notion-client-id", "notion.oauth.client-secret=test-notion-client-secret",
        "notion.oauth.callback-uri=https://api.example.com/api/v1/notion/oauth/callback",
        "notion.oauth.success-redirect-uri=https://app.example.com/notion-connection?result=connected",
        "notion.oauth.failure-redirect-uri=https://app.example.com/notion-connection?result=failed",
        "notion.oauth.state-hash-key=bm90aW9uLXN0YXRlLWhhc2gta2V5LTAwMDAwMDAwMDA",
        "notion.oauth.encryption-keys.v1=bm90aW9uLWVuY3J5cHRpb24ta2V5LTAwMDAwMDAwMDA",
        "spring.datasource.username=knot", "spring.datasource.password=knot",
        "spring.security.oauth2.client.registration.github.client-id=test-client-id",
        "spring.security.oauth2.client.registration.github.client-secret=test-client-secret"})
public @interface TestApplicationProperties {
}
