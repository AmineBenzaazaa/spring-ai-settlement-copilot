package com.msetransport.copilot.config;

import com.msetransport.copilot.knowledge.HashingEmbeddingModel;
import com.msetransport.copilot.knowledge.KnowledgeTools;
import com.msetransport.copilot.tools.SettlementTools;
import com.msetransport.copilot.tools.TracingToolCallback;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AiConfig {

    @Bean
    CopilotToolbox copilotToolbox(SettlementTools settlementTools, KnowledgeTools knowledgeTools) {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
                .toolObjects(settlementTools, knowledgeTools)
                .build()
                .getToolCallbacks();
        return new CopilotToolbox(Arrays.stream(callbacks).<ToolCallback>map(TracingToolCallback::new).toList());
    }

    /** Offline embeddings for RAG, in every mode (Anthropic has no embeddings API). */
    @Bean
    EmbeddingModel embeddingModel() {
        return new HashingEmbeddingModel(HashingEmbeddingModel.DEFAULT_DIMENSIONS);
    }

    /** Sliding window over the JDBC repository: the last N messages of a conversation go back to the model. */
    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository, CopilotProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.memoryMaxMessages())
                .build();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Spring AI 1.1 defaults {@code temperature} to 0.8, but current Claude models reject sampling
     * parameters with a 400. Drop it unless someone configured one on purpose.
     */
    @Bean
    static BeanPostProcessor anthropicSamplingDefaults(org.springframework.core.env.Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof AnthropicChatProperties props
                        && environment.getProperty("copilot.anthropic.temperature") == null) {
                    props.getOptions().setTemperature(null);
                }
                return bean;
            }
        };
    }
}
