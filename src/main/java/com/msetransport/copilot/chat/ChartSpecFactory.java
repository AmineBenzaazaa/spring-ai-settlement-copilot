package com.benzaazaa.copilot.chat;

import com.benzaazaa.copilot.chat.ChartSpec.Dataset;
import com.benzaazaa.copilot.tools.ToolOutputs.CourierBalance;
import com.benzaazaa.copilot.tools.ToolOutputs.CourierRanking;
import com.benzaazaa.copilot.tools.ToolOutputs.CourierRow;
import com.benzaazaa.copilot.tools.ToolOutputs.DisputeList;
import com.benzaazaa.copilot.tools.ToolOutputs.DisputeRow;
import com.benzaazaa.copilot.tools.ToolOutputs.Overview;
import com.benzaazaa.copilot.tools.ToolOutputs.StatementExplanation;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Picks the most useful chart for what the tools returned in this turn (the last chartable output wins). */
@Component
public class ChartSpecFactory {

    private static final String MAD = "MAD";

    public Optional<ChartSpec> from(List<Object> outputs, Language lang) {
        for (int i = outputs.size() - 1; i >= 0; i--) {
            Optional<ChartSpec> chart = chartFor(outputs.get(i), lang == Language.FR);
            if (chart.isPresent()) {
                return chart;
            }
        }
        return Optional.empty();
    }

    private Optional<ChartSpec> chartFor(Object output, boolean fr) {
        return switch (output) {
            case CourierRanking r when !r.couriers().isEmpty() -> Optional.of(new ChartSpec("bar",
                    fr ? "Solde dû par livreur" : "Balance owed by courier",
                    r.couriers().stream().map(CourierRow::name).toList(),
                    List.of(new Dataset(fr ? "Solde dû" : "Balance owed",
                            r.couriers().stream().map(c -> amount(c.balanceOwed())).toList())), MAD));
            case Overview o when !o.couriers().isEmpty() -> Optional.of(new ChartSpec("bar",
                    fr ? "Attendu vs déclaré par livreur" : "Expected vs reported by courier",
                    o.couriers().stream().map(CourierRow::name).toList(),
                    List.of(new Dataset(fr ? "Attendu" : "Expected",
                                    o.couriers().stream().map(c -> amount(c.expectedCollections())).toList()),
                            new Dataset(fr ? "Déclaré" : "Reported",
                                    o.couriers().stream().map(c -> amount(c.reportedCollections())).toList())), MAD));
            case CourierBalance b -> Optional.of(new ChartSpec("bar", b.courierName(),
                    fr ? List.of("Attendu", "Déclaré", "Frais", "Versé net", "Solde dû")
                            : List.of("Expected", "Reported", "Fees", "Net remitted", "Balance owed"),
                    List.of(new Dataset(MAD, List.of(amount(b.expectedCollections()), amount(b.reportedCollections()),
                            amount(b.fees()), amount(b.netRemitted()), amount(b.balanceOwed())))), MAD));
            case DisputeList d when !d.disputes().isEmpty() -> Optional.of(new ChartSpec("bar",
                    fr ? "Montant réclamé par litige" : "Claimed amount per dispute",
                    d.disputes().stream().map(x -> "#" + x.id() + " " + x.trackingNumber()).toList(),
                    List.of(new Dataset(fr ? "Réclamé" : "Claimed",
                            d.disputes().stream().map(DisputeRow::claimedAmount).map(ChartSpecFactory::amount).toList())),
                    MAD));
            case StatementExplanation s when !s.counts().isEmpty() -> {
                List<String> labels = new ArrayList<>();
                List<BigDecimal> data = new ArrayList<>();
                for (Map.Entry<String, Long> e : s.counts().entrySet()) {
                    if (e.getValue() > 0) {
                        labels.add(e.getKey());
                        data.add(BigDecimal.valueOf(e.getValue()));
                    }
                }
                yield Optional.of(new ChartSpec("doughnut",
                        (fr ? "Lignes du relevé #" : "Lines of statement #") + s.statementId(), labels,
                        List.of(new Dataset(fr ? "Lignes" : "Lines", data)), null));
            }
            default -> Optional.empty();
        };
    }

    private static BigDecimal amount(String value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value);
    }
}
