package com.benzaazaa.copilot.demo;

import com.benzaazaa.copilot.config.CopilotProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the offline model only in demo mode; in anthropic mode Spring AI provides Claude's ChatModel. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "copilot.mode", havingValue = "demo")
public class DemoModelConfig {

    @Bean
    ChatModel scriptedChatModel(ObjectMapper objectMapper, CopilotProperties properties) {
        return new ScriptedChatModel(new DemoPlanner(), new AnswerRenderer(), objectMapper, properties.demo().tokenDelay());
    }
}
