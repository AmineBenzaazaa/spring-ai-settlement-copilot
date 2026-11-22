package com.msetransport.copilot.config;

import com.msetransport.copilot.knowledge.KnowledgeTools;
import com.msetransport.copilot.tools.SettlementTools;

/** Lets tests outside the package build the same toolbox the application uses. */
public final class AiConfigTestAccess {

    private AiConfigTestAccess() {
    }

    public static CopilotToolbox toolbox(SettlementTools settlementTools, KnowledgeTools knowledgeTools) {
        return new AiConfig().copilotToolbox(settlementTools, knowledgeTools);
    }
}
