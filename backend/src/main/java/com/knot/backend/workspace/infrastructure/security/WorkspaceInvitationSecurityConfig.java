package com.knot.backend.workspace.infrastructure.security;

import com.knot.backend.workspace.application.WorkspaceInvitationSecretGenerator;
import com.knot.backend.workspace.application.WorkspaceInvitationSecretProtector;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WorkspaceInvitationSecurityProperties.class)
public class WorkspaceInvitationSecurityConfig {

    @Bean
    public WorkspaceInvitationSecretGenerator workspaceInvitationSecretGenerator() {
        return new SecureWorkspaceInvitationSecretGenerator();
    }

    @Bean
    public WorkspaceInvitationSecretProtector workspaceInvitationSecretProtector(
            WorkspaceInvitationSecurityProperties properties
    ) {
        return new AesGcmWorkspaceInvitationSecretProtector(properties);
    }
}
