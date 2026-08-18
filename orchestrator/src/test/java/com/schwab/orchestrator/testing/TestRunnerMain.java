package com.schwab.orchestrator.testing;

import com.schwab.orchestrator.api.OrchestratorApiIntegrationTest;
import com.schwab.orchestrator.execution.ExecutionEngineTest;
import com.schwab.orchestrator.governance.ComplianceGuardrailTest;
import com.schwab.orchestrator.governance.SecurityGuardrailTest;
import com.schwab.orchestrator.graph.DependencyGraphTest;
import com.schwab.orchestrator.reliability.MetricsRegistryTest;

public final class TestRunnerMain {
    public static void main(String[] args) {
        System.out.println("Running orchestrator test suite (no ANTHROPIC_API_KEY expected in this environment -> exercises fallback path)...\n");
        var results = MicroTest.run(
                DependencyGraphTest.class,
                SecurityGuardrailTest.class,
                ComplianceGuardrailTest.class,
                MetricsRegistryTest.class,
                ExecutionEngineTest.class,
                OrchestratorApiIntegrationTest.class
        );
        int failed = MicroTest.report(results);
        if (failed > 0) System.exit(1);
    }
}
