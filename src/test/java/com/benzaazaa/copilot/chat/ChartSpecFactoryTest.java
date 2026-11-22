package com.benzaazaa.copilot.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.benzaazaa.copilot.tools.ToolOutputs;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChartSpecFactoryTest {

    private final ChartSpecFactory factory = new ChartSpecFactory();
    private final ToolOutputs.CourierRow rapido = new ToolOutputs.CourierRow(2L, "Rapido Express", "rapido", 9,
            "4315.00", "0.00", "0.00", "0.00", "4315.00");
    private final ToolOutputs.CourierRow speedex = new ToolOutputs.CourierRow(1L, "Speedex Colis", "speedex", 8,
            "4588.00", "4057.50", "225.00", "3832.50", "530.50");

    @Test
    void rankingBecomesABarChartOfBalances() {
        ChartSpec chart = factory.from(List.of(new ToolOutputs.CourierRanking("MAD", "4845.50", List.of(rapido, speedex))),
                Language.EN).orElseThrow();
        assertThat(chart.type()).isEqualTo("bar");
        assertThat(chart.labels()).containsExactly("Rapido Express", "Speedex Colis");
        assertThat(chart.datasets().getFirst().data()).containsExactly(new BigDecimal("4315.00"), new BigDecimal("530.50"));
        assertThat(chart.unit()).isEqualTo("MAD");
    }

    @Test
    void overviewComparesExpectedAndReportedInFrench() {
        ChartSpec chart = factory.from(List.of(new ToolOutputs.Overview("MAD", "1", "1", "0", "1", "0", 0, "0.00", 0, "0.00",
                List.of(speedex))), Language.FR).orElseThrow();
        assertThat(chart.title()).isEqualTo("Attendu vs déclaré par livreur");
        assertThat(chart.datasets()).hasSize(2);
    }

    @Test
    void statementBecomesADoughnutOfNonZeroOutcomes() {
        ToolOutputs.StatementExplanation s = new ToolOutputs.StatementExplanation(1L, 1L, "2026-10-01", "2026-10-31",
                "RECONCILED", 13, true, Map.of("MATCHED", 11L, "AMOUNT_MISMATCH", 1L, "UNKNOWN_TRACKING", 0L),
                "1", "1", "1", "1", "0", List.of());
        ChartSpec chart = factory.from(List.of(s), Language.EN).orElseThrow();
        assertThat(chart.type()).isEqualTo("doughnut");
        assertThat(chart.labels()).containsExactlyInAnyOrder("MATCHED", "AMOUNT_MISMATCH");
        assertThat(chart.unit()).isNull();
    }

    @Test
    void disputesBalanceAndFallbacks() {
        ToolOutputs.DisputeList disputes = new ToolOutputs.DisputeList("OPEN", null, 1, "50.00", "50.00", 1, "50.00",
                List.of(new ToolOutputs.DisputeRow(1L, 1L, "SPX-1", "AMOUNT_MISMATCH", "50.00", "OPEN", "r", null)));
        assertThat(factory.from(List.of(disputes), Language.EN).orElseThrow().labels()).containsExactly("#1 SPX-1");
        ToolOutputs.CourierBalance balance = new ToolOutputs.CourierBalance(1L, "Speedex Colis", "MAD", 8, "4588.00",
                "4057.50", "225.00", "3832.50", "530.50");
        assertThat(factory.from(List.of(balance), Language.FR).orElseThrow().labels()).contains("Solde dû");
        // the last chartable output wins; non-chartable outputs are skipped
        assertThat(factory.from(List.of(balance, new ToolOutputs.StatementList(0, List.of())), Language.EN)).isPresent();
        assertThat(factory.from(List.of(new ToolOutputs.DisputeList(null, null, 0, "0.00", null, 0, null, List.of())),
                Language.EN)).isEmpty();
        assertThat(factory.from(List.of(), Language.EN)).isEmpty();
    }
}
