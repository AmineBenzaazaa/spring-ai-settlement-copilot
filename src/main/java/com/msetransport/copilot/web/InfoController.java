package com.msetransport.copilot.web;

import com.msetransport.copilot.config.CopilotProperties;
import com.msetransport.copilot.config.CopilotToolbox;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public: lets the UI show which mode is running (offline demo or Claude) before anyone signs in. */
@RestController
@Tag(name = "Info")
public class InfoController {

    private final CopilotProperties properties;
    private final CopilotToolbox toolbox;

    public InfoController(CopilotProperties properties, CopilotToolbox toolbox) {
        this.properties = properties;
        this.toolbox = toolbox;
    }

    @GetMapping("/api/copilot/info")
    public Info info() {
        boolean demo = properties.mode() == CopilotProperties.CopilotMode.DEMO;
        return new Info(properties.mode().label(), properties.activeModel(),
                demo ? "Offline demo model: deterministic, no API key, same tools and guards as Claude"
                        : "Anthropic Claude via Spring AI",
                List.of("en", "fr"), toolbox.names(), properties.deskBaseUrl());
    }

    public record Info(String mode, String model, String description, List<String> languages, List<String> tools,
                       String deskUrl) {
    }
}
