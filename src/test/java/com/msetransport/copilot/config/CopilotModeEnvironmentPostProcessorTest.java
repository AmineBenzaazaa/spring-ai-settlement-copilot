package com.msetransport.copilot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CopilotModeEnvironmentPostProcessorTest {

    private final CopilotModeEnvironmentPostProcessor processor = new CopilotModeEnvironmentPostProcessor();

    private MockEnvironment run(String mode, String key) {
        MockEnvironment env = new MockEnvironment();
        if (mode != null) {
            env.setProperty("copilot.mode", mode);
        }
        if (key != null) {
            env.setProperty("spring.ai.anthropic.api-key", key);
        }
        processor.postProcessEnvironment(env, null);
        return env;
    }

    @Test
    void autoWithoutKeyIsDemoAndSwitchesOffAnthropic() {
        MockEnvironment env = run(null, "");
        assertThat(env.getProperty("copilot.mode")).isEqualTo("demo");
        assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("none");
        assertThat(env.getProperty("spring.ai.model.embedding")).isEqualTo("none");
    }

    @Test
    void autoWithKeyIsAnthropic() {
        MockEnvironment env = run("auto", "k");
        assertThat(env.getProperty("copilot.mode")).isEqualTo("anthropic");
        assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("anthropic");
    }

    @Test
    void demoWinsEvenWithAKey() {
        assertThat(run("DEMO", "k").getProperty("copilot.mode")).isEqualTo("demo");
    }

    @Test
    void anthropicNeedsAKeyAndUnknownModesFail() {
        assertThatThrownBy(() -> run("anthropic", null)).hasMessageContaining("ANTHROPIC_API_KEY");
        assertThat(run("anthropic", "k").getProperty("copilot.mode")).isEqualTo("anthropic");
        assertThatThrownBy(() -> run("gpt", null)).hasMessageContaining("auto, demo or anthropic");
        assertThat(processor.getOrder()).isEqualTo(Integer.MAX_VALUE);
    }
}
