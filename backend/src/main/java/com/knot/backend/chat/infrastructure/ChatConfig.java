package com.knot.backend.chat.infrastructure;

import com.knot.backend.chat.application.ChatProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatProperties.class)
public class ChatConfig {}
