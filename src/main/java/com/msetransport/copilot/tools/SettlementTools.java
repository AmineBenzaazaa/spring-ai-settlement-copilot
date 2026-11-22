package com.msetransport.copilot.tools;

import com.msetransport.copilot.guard.InjectionGuard;
import com.msetransport.copilot.settlement.DeskLinks;
import com.msetransport.copilot.settlement.SettlementApiClient;
import com.msetransport.copilot.settlement.SettlementApiClient.OrderQuery;
import com.msetransport.copilot.settlement.SettlementApiException;
import com.msetransport.copilot.settlement.SettlementModels;
import com.msetransport.copilot.tools.ToolOutputs.CourierBalance;
import com.msetransport.copilot.tools.ToolOutputs.CourierMatches;
import com.msetransport.copilot.tools.ToolOutputs.CourierRanking;
import com.msetransport.copilot.tools.ToolOutputs.CourierRef;
import com.msetransport.copilot.tools.ToolOutputs.CourierRow;
import com.msetransport.copilot.tools.ToolOutputs.DisputeList;
import com.msetransport.copilot.tools.ToolOutputs.DisputeRow;
import com.msetransport.copilot.tools.ToolOutputs.IssueRow;
import com.msetransport.copilot.tools.ToolOutputs.OrderList;
import com.msetransport.copilot.tools.ToolOutputs.OrderRow;
import com.msetransport.copilot.tools.ToolOutputs.Overview;
import com.msetransport.copilot.tools.ToolOutputs.StatementExplanation;
import com.msetransport.copilot.tools.ToolOutputs.StatementList;
import com.msetransport.copilot.tools.ToolOutputs.StatementRow;
import com.msetransport.copilot.tools.ToolOutputs.ToolError;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Read-only tools over the settlement platform. Each one calls the platform's real REST API
 * with the caller's own token (taken from the {@link TurnContext}, never from the model), shapes
 * the answer into a small JSON object and records the rows it used as sources.
 *
 * <p>No tool can write: the copilot explains data, it never opens or resolves a dispute.
 */
@Component
public class SettlementTools {

    private static final Set<String> DISPUTE_STATUSES = Set.of("OPEN", "RESOLVED", "REJECTED");
    private static final Set<String> STATEMENT_STATUSES = Set.of("UPLOADED", "PROCESSING", "RECONCILED", "FAILED");
    private static final Set<String> OUTCOMES = Set.of("MATCHED", "AMOUNT_MISMATCH", "UNKNOWN_TRACKING", "MISSING_FROM_STATEMENT");
    private static final Set<String> ORDER_STATUSES = Set.of("CREATED", "SHIPPED", "DELIVERED", "RETURNED", "CANCELLED");

    private final SettlementApiClient client;
    private final DeskLinks links;
    private final InjectionGuard injectionGuard;

    public SettlementTools(SettlementApiClient client, DeskLinks links, InjectionGuard injectionGuard) {
        this.client = client;
        this.links = links;
        this.injectionGuard = injectionGuard;
    }

    @Tool(name = "getSettlementOverview", description = """
            Totals for the signed-in merchant: COD expected vs reported by couriers, fees, net remitted,
            balance still owed, number and amount of open disputes, number and amount of discrepancies,
            plus one row per courier. Use for general 'how are we doing' or summary questions.""")
    public Object getSettlementOverview(ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        return guarded(turn, "overview", () -> {
            SettlementModels.Overview o = client.overview(turn.token());
            List<CourierRow> rows = o.couriers().stream().map(SettlementTools::row).toList();
            Overview out = new Overview(o.currency(), Money.format(o.expectedCollections()),
                    Money.format(o.reportedCollections()), Money.format(o.fees()), Money.format(o.netRemitted()),
                    Money.format(o.balanceOwed()), o.openDisputes(), Money.format(o.openDisputedAmount()),
                    o.discrepancies(), Money.format(o.discrepancyAmount()), rows);
            turn.recordResult(out, rows.size(), 0, List.of(new SourceRef("overview", "overview",
                    "Settlement overview", links.overview())));
            return out;
        });
    }

    @Tool(name = "getCourierBalances", description = """
            All couriers ranked by the cash they still owe the merchant (balanceOwed, largest first), with
            expected and reported collections, fees and net remitted. Use for 'which courier owes the most',
            rankings and comparisons between couriers.""")
    public Object getCourierBalances(ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        return guarded(turn, "couriers", () -> {
            SettlementModels.Overview o = client.overview(turn.token());
            List<SettlementModels.CourierPosition> sorted = o.couriers().stream()
                    .sorted(Comparator.comparing((SettlementModels.CourierPosition c) -> Money.orZero(c.balanceOwed()))
                            .reversed())
                    .toList();
            BigDecimal total = sorted.stream().map(c -> Money.orZero(c.balanceOwed())).reduce(BigDecimal.ZERO, BigDecimal::add);
            List<CourierRow> rows = sorted.stream().map(SettlementTools::row).toList();
            CourierRanking out = new CourierRanking(o.currency(), Money.format(total), rows);
            turn.recordResult(out, rows.size(), 0, rows.stream()
                    .map(r -> new SourceRef("courier", String.valueOf(r.courierId()), r.name(), links.couriers()))
                    .toList());
            return out;
        });
    }

    @Tool(name = "findCourier", description = """
            Look up couriers by (part of) their name or code, e.g. 'speedex'. Returns their ids. Call this
            first when the user names a courier, then use the id with getCourierBalance. Pass an empty
            query to list every courier.""")
    public Object findCourier(@ToolParam(description = "Name or code to search, may be empty", required = false) String query,
                              ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        return guarded(turn, "couriers", () -> {
            List<CourierRef> refs = client.couriers(turn.token(), clean(query)).content().stream()
                    .map(c -> new CourierRef(c.id(), c.name(), c.code(), c.active()))
                    .toList();
            CourierMatches out = new CourierMatches(clean(query), refs);
            turn.recordResult(out, refs.size(), 0, List.of());
            return out;
        });
    }

    @Tool(name = "getCourierBalance", description = """
            Cash position of ONE courier for the signed-in merchant: delivered orders, expected collections,
            collections the courier reported, fees, net remitted and balanceOwed (= expected - reported).""")
    public Object getCourierBalance(@ToolParam(description = "Courier id, from findCourier") Long courierId,
                                    ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        if (courierId == null || courierId <= 0) {
            return invalid(turn, "courierId must be a positive number; call findCourier first");
        }
        return guarded(turn, "courier " + courierId, () -> {
            SettlementModels.CourierBalance b = client.courierBalance(turn.token(), courierId);
            String name = client.couriers(turn.token(), null).content().stream()
                    .filter(c -> Objects.equals(c.id(), courierId)).map(SettlementModels.Courier::name)
                    .findFirst().orElse("Courier " + courierId);
            CourierBalance out = new CourierBalance(b.courierId(), name, b.currency(), b.deliveredOrders(),
                    Money.format(b.expectedCollections()), Money.format(b.reportedCollections()), Money.format(b.fees()),
                    Money.format(b.netRemitted()), Money.format(b.balanceOwed()));
            turn.recordResult(out, 1, 0, List.of(new SourceRef("courier", String.valueOf(courierId), name, links.couriers())));
            return out;
        });
    }

    @Tool(name = "listDisputes", description = """
            Disputes the merchant opened on reconciliation discrepancies, largest claimed amount first.
            Optional filters: status (OPEN, RESOLVED, REJECTED), minimum claimed amount in MAD, courier id,
            statement id. Returns the count, total and largest claimed amount, and each dispute.""")
    public Object listDisputes(
            @ToolParam(description = "OPEN, RESOLVED or REJECTED", required = false) String status,
            @ToolParam(description = "Only disputes claiming strictly more than this amount, in MAD", required = false) BigDecimal minAmount,
            @ToolParam(description = "Courier id", required = false) Long courierId,
            @ToolParam(description = "Statement id", required = false) Long statementId,
            ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        String st = upper(status);
        if (st != null && !DISPUTE_STATUSES.contains(st)) {
            return invalid(turn, "status must be one of " + DISPUTE_STATUSES);
        }
        return guarded(turn, "disputes", () -> {
            int[] flagged = {0};
            List<SettlementModels.Dispute> all = client.disputes(turn.token(), st, courierId, statementId).content();
            String largestOverall = all.stream().map(d -> Money.orZero(d.claimedAmount())).max(Comparator.naturalOrder())
                    .map(Money::format).orElse(null);
            List<DisputeRow> rows = all.stream()
                    .filter(d -> minAmount == null || Money.orZero(d.claimedAmount()).compareTo(minAmount) > 0)
                    .sorted(Comparator.comparing((SettlementModels.Dispute d) -> Money.orZero(d.claimedAmount())).reversed())
                    .map(d -> {
                        InjectionGuard.Sanitized reason = injectionGuard.sanitize(d.reason());
                        if (reason.flagged()) {
                            flagged[0]++;
                        }
                        return new DisputeRow(d.id(), d.statementId(), d.trackingNumber(), d.outcome(),
                                Money.format(d.claimedAmount()), d.status(), reason.text(),
                                d.createdAt() == null ? null : d.createdAt().toString());
                    })
                    .toList();
            BigDecimal total = rows.stream().map(r -> new BigDecimal(r.claimedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            String largest = rows.isEmpty() ? null : rows.getFirst().claimedAmount();
            DisputeList out = new DisputeList(st, Money.format(minAmount), rows.size(), Money.format(total), largest,
                    all.size(), largestOverall, rows);
            turn.recordResult(out, rows.size(), flagged[0], rows.stream()
                    .map(r -> new SourceRef("dispute", String.valueOf(r.id()),
                            "Dispute #" + r.id() + " · " + r.trackingNumber(), links.disputes()))
                    .toList());
            return out;
        });
    }

    @Tool(name = "getStatementReconciliation", description = """
            Explains one courier remittance statement: period, status, line count, totals (expected,
            collected, fees, net remitted, difference) and every line that is NOT matched (AMOUNT_MISMATCH,
            UNKNOWN_TRACKING, MISSING_FROM_STATEMENT) with expected/collected/difference. Use for
            'why was statement #N flagged' or 'what is wrong with statement N'.""")
    public Object getStatementReconciliation(
            @ToolParam(description = "Statement id, the number after #") Long statementId,
            @ToolParam(description = "Optional outcome filter", required = false) String outcome,
            ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        if (statementId == null || statementId <= 0) {
            return invalid(turn, "statementId must be a positive number");
        }
        String oc = upper(outcome);
        if (oc != null && !OUTCOMES.contains(oc)) {
            return invalid(turn, "outcome must be one of " + OUTCOMES);
        }
        return guarded(turn, "statement " + statementId, () -> {
            SettlementModels.ReconciliationReport r = client.reconciliation(turn.token(), statementId, oc);
            SettlementModels.Statement s = r.statement();
            List<IssueRow> issues = r.results().stream()
                    .filter(x -> !"MATCHED".equals(x.outcome()))
                    .map(x -> new IssueRow(x.id(), x.outcome(), x.trackingNumber(), Money.format(x.expectedAmount()),
                            Money.format(x.collectedAmount()), Money.format(x.difference()), x.hasDispute()))
                    .toList();
            SettlementModels.Summary sum = r.summary();
            Map<String, Long> counts = sum == null ? Map.of() : new TreeMap<>(sum.counts());
            StatementExplanation out = new StatementExplanation(s.id(), s.courierId(), str(s.periodStart()),
                    str(s.periodEnd()), s.status(), s.lineCount(), !issues.isEmpty(), counts,
                    sum == null ? null : Money.format(sum.expectedTotal()),
                    sum == null ? null : Money.format(sum.collectedTotal()),
                    sum == null ? null : Money.format(sum.feesTotal()),
                    sum == null ? null : Money.format(sum.netRemitted()),
                    sum == null ? null : Money.format(sum.differenceTotal()), issues);
            turn.recordResult(out, issues.size(), 0, List.of(new SourceRef("statement", String.valueOf(s.id()),
                    "Statement #" + s.id() + " (" + s.periodStart() + " → " + s.periodEnd() + ")", links.statement(s.id()))));
            return out;
        });
    }

    @Tool(name = "listStatements", description = """
            The merchant's uploaded courier remittance statements, newest first: id, courier id, period,
            status (UPLOADED, PROCESSING, RECONCILED, FAILED) and line count.""")
    public Object listStatements(
            @ToolParam(description = "UPLOADED, PROCESSING, RECONCILED or FAILED", required = false) String status,
            @ToolParam(description = "Courier id", required = false) Long courierId,
            ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        String st = upper(status);
        if (st != null && !STATEMENT_STATUSES.contains(st)) {
            return invalid(turn, "status must be one of " + STATEMENT_STATUSES);
        }
        return guarded(turn, "statements", () -> {
            List<StatementRow> rows = client.statements(turn.token(), st, courierId).content().stream()
                    .map(s -> new StatementRow(s.id(), s.courierId(), str(s.periodStart()), str(s.periodEnd()),
                            s.status(), s.lineCount(), s.uploadedAt() == null ? null : s.uploadedAt().toString()))
                    .toList();
            StatementList out = new StatementList(rows.size(), rows);
            turn.recordResult(out, rows.size(), 0, rows.stream()
                    .map(r -> new SourceRef("statement", String.valueOf(r.id()), "Statement #" + r.id(), links.statement(r.id())))
                    .toList());
            return out;
        });
    }

    @Tool(name = "searchOrders", description = """
            Search the merchant's COD orders, most expensive first (at most 50 shown, totalMatching gives the
            full count). Filters: status (CREATED, SHIPPED, DELIVERED, RETURNED, CANCELLED), city, courier id,
            min/max amount due in MAD, free text (tracking number, customer name, phone).""")
    public Object searchOrders(
            @ToolParam(description = "Order status", required = false) String status,
            @ToolParam(description = "City, e.g. Casablanca", required = false) String city,
            @ToolParam(description = "Courier id", required = false) Long courierId,
            @ToolParam(description = "Minimum amount due in MAD", required = false) BigDecimal minAmount,
            @ToolParam(description = "Maximum amount due in MAD", required = false) BigDecimal maxAmount,
            @ToolParam(description = "Free text: tracking number, customer name or phone", required = false) String text,
            ToolContext toolContext) {
        TurnContext turn = TurnContext.from(toolContext);
        String st = upper(status);
        if (st != null && !ORDER_STATUSES.contains(st)) {
            return invalid(turn, "status must be one of " + ORDER_STATUSES);
        }
        return guarded(turn, "orders", () -> {
            SettlementModels.Page<SettlementModels.Order> page = client.orders(turn.token(),
                    new OrderQuery(st, clean(city), courierId, minAmount, maxAmount, clean(text)));
            int[] flagged = {0};
            List<OrderRow> rows = page.content().stream().map(o -> {
                InjectionGuard.Sanitized name = injectionGuard.sanitize(o.customerName());
                if (name.flagged()) {
                    flagged[0]++;
                }
                return new OrderRow(o.id(), o.trackingNumber(), name.text(), o.city(), Money.format(o.amountDue()),
                        o.status(), o.courierName());
            }).toList();
            BigDecimal total = page.content().stream().map(o -> Money.orZero(o.amountDue())).reduce(BigDecimal.ZERO, BigDecimal::add);
            OrderList out = new OrderList(page.totalElements(), rows.size(), Money.format(total), rows);
            List<SourceRef> sources = new ArrayList<>();
            rows.stream().limit(10).forEach(r -> sources.add(new SourceRef("order", String.valueOf(r.id()),
                    r.trackingNumber(), links.orders(r.trackingNumber()))));
            turn.recordResult(out, rows.size(), flagged[0], sources);
            return out;
        });
    }

    // ---------------------------------------------------------------------------------------------

    /** Turns platform failures into a {@link ToolError} the model can explain instead of a stack trace. */
    private Object guarded(TurnContext turn, String what, Supplier<Object> call) {
        try {
            return call.get();
        } catch (SettlementApiException.NotFound ex) {
            turn.recordFailure("NOT_FOUND");
            return new ToolError("NOT_FOUND", "No " + what + " found for this account. It does not exist or belongs to someone else.");
        } catch (SettlementApiException.AccessDenied ex) {
            turn.recordFailure("DENIED");
            return new ToolError("ACCESS_DENIED", "The settlement platform refused access to " + what + " for this user.");
        } catch (SettlementApiException.BadRequest ex) {
            turn.recordFailure("ERROR");
            return new ToolError("BAD_REQUEST", "The settlement platform rejected the filter for " + what + ".");
        } catch (SettlementApiException.Unavailable | CallNotPermittedException ex) {
            turn.recordFailure("UNAVAILABLE");
            return new ToolError("UNAVAILABLE", "The settlement platform is not reachable right now.");
        }
    }

    private static ToolError invalid(TurnContext turn, String message) {
        turn.recordFailure("ERROR");
        return new ToolError("INVALID_ARGUMENT", message);
    }

    private static CourierRow row(SettlementModels.CourierPosition c) {
        return new CourierRow(c.courierId(), c.name(), c.code(), c.deliveredOrders(), Money.format(c.expectedCollections()),
                Money.format(c.reportedCollections()), Money.format(c.fees()), Money.format(c.netRemitted()),
                Money.format(c.balanceOwed()));
    }

    private static String upper(String value) {
        String v = clean(value);
        return v == null ? null : v.toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
