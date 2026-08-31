package com.knot.backend.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.TestPropertySource;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@TestPropertySource(properties = {"notion.oauth.enabled=true", "notion.oauth.client-id=test-notion-client-id",
        "notion.oauth.client-secret=test-notion-client-secret",
        "notion.oauth.callback-uri=https://api.example.com/api/v1/notion/oauth/callback",
        "notion.oauth.state-hash-key=bm90aW9uLXN0YXRlLWhhc2gta2V5LTAwMDAwMDAwMDA",
        "notion.oauth.encryption-keys.v1=bm90aW9uLWVuY3J5cHRpb24ta2V5LTAwMDAwMDAwMDA"})
public @interface NotionOAuthTestProperties {
}
