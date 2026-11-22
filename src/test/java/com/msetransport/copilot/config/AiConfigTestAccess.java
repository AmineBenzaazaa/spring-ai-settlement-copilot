package com.benzaazaa.copilot.config;

import com.benzaazaa.copilot.knowledge.KnowledgeTools;
import com.benzaazaa.copilot.tools.SettlementTools;

/** Lets tests outside the package build the same toolbox the application uses. */
public final class AiConfigTestAccess {

    private AiConfigTestAccess() {
    }

    public static CopilotToolbox toolbox(SettlementTools settlementTools, KnowledgeTools knowledgeTools) {
        return new AiConfig().copilotToolbox(settlementTools, knowledgeTools);
    }
}
