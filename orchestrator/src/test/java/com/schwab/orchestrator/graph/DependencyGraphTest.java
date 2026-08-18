package com.schwab.orchestrator.graph;

import com.schwab.orchestrator.model.ScenarioType;
import com.schwab.orchestrator.testing.MicroTest;

import java.util.List;
import java.util.Set;

public class DependencyGraphTest {

    @MicroTest.Test
    public void greenfieldGraphExcludesCodebaseReasoning() {
        DependencyGraph g = new GraphBuilder().build(ScenarioType.GREENFIELD);
        MicroTest.assertFalse(g.contains(StageId.CODEBASE_REASONING), "greenfield graph should not include CODEBASE_REASONING");
        MicroTest.assertTrue(g.contains(StageId.IMPLEMENTATION), "graph should always include IMPLEMENTATION");
    }

    @MicroTest.Test
    public void brownfieldGraphIncludesCodebaseReasoning() {
        DependencyGraph g = new GraphBuilder().build(ScenarioType.BROWNFIELD);
        MicroTest.assertTrue(g.contains(StageId.CODEBASE_REASONING), "brownfield graph should include CODEBASE_REASONING");
        MicroTest.assertTrue(g.get(StageId.ARCHITECTURE_DESIGN).dependsOn().contains(StageId.CODEBASE_REASONING),
                "architecture design should depend on codebase reasoning in brownfield");
    }

    @MicroTest.Test
    public void topologicalBatchesRespectDependencyOrder() {
        DependencyGraph g = new GraphBuilder().build(ScenarioType.GREENFIELD);
        List<Set<StageId>> batches = g.topologicalBatches();
        int reqBatch = indexOfBatchContaining(batches, StageId.REQUIREMENT_UNDERSTANDING);
        int implBatch = indexOfBatchContaining(batches, StageId.IMPLEMENTATION);
        int testBatch = indexOfBatchContaining(batches, StageId.TESTING);
        int docBatch = indexOfBatchContaining(batches, StageId.DOCUMENTATION);
        int releaseBatch = indexOfBatchContaining(batches, StageId.RELEASE_READINESS);

        MicroTest.assertTrue(reqBatch < implBatch, "requirement understanding must batch before implementation");
        MicroTest.assertTrue(implBatch < testBatch, "implementation must batch before testing");
        MicroTest.assertEquals(testBatch, docBatch, "testing and documentation should be in the SAME batch (parallel fork)");
        MicroTest.assertTrue(testBatch < releaseBatch, "release readiness must batch after testing/documentation (join)");
    }

    @MicroTest.Test
    public void cycleIsRejected() {
        DependencyGraph g = new DependencyGraph();
        g.addStage(new StageDefinition(StageId.REQUIREMENT_UNDERSTANDING, "A", Set.of(), GateType.NONE, GateType.NONE, 1, List.of()));
        g.addStage(new StageDefinition(StageId.TASK_DECOMPOSITION, "B", Set.of(StageId.REQUIREMENT_UNDERSTANDING), GateType.NONE, GateType.NONE, 1, List.of()));
        MicroTest.assertThrows(IllegalStateException.class, () ->
                        g.replaceStage(new StageDefinition(StageId.REQUIREMENT_UNDERSTANDING, "A", Set.of(StageId.TASK_DECOMPOSITION), GateType.NONE, GateType.NONE, 1, List.of())),
                "introducing a cycle should be rejected");
    }

    private int indexOfBatchContaining(List<Set<StageId>> batches, StageId id) {
        for (int i = 0; i < batches.size(); i++) {
            if (batches.get(i).contains(id)) return i;
        }
        throw new IllegalStateException("Stage not found in any batch: " + id);
    }
}
