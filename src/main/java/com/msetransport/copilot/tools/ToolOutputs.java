package com.msetransport.copilot.tools;

import java.util.List;
import java.util.Map;

/**
 * What the tools hand back to the model, serialized to JSON by Spring AI. Small, flat, money as
 * two-decimal strings, and only the fields an answer needs: fewer tokens, fewer chances to misread.
 */
public final class ToolOutputs {

    private ToolOutputs() {
    }

    /** Returned instead of data when the platform says no; the model must then say so, not guess. */
    public record ToolError(String error, String message) {
    }

    public record CourierRow(Long courierId, String name, String code, long deliveredOrders, String expectedCollections,
                             String reportedCollections, String fees, String netRemitted, String balanceOwed) {
    }

    public record Overview(String currency, String expectedCollections, String reportedCollections, String fees,
                           String netRemitted, String balanceOwed, long openDisputes, String openDisputedAmount,
                           long discrepancies, String discrepancyAmount, List<CourierRow> couriers) {
    }

    /** Couriers sorted by what they still owe, largest first. */
    public record CourierRanking(String currency, String totalOwed, List<CourierRow> couriers) {
    }

    public record CourierRef(Long id, String name, String code, boolean active) {
    }

    public record CourierMatches(String query, List<CourierRef> couriers) {
    }

    public record CourierBalance(Long courierId, String courierName, String currency, long deliveredOrders,
                                 String expectedCollections, String reportedCollections, String fees,
                                 String netRemitted, String balanceOwed) {
    }

    public record DisputeRow(Long id, Long statementId, String trackingNumber, String outcome, String claimedAmount,
                             String status, String reason, String createdAt) {
    }

    /**
     * @param minAmount           the amount filter applied, {@code null} when none
     * @param countIgnoringAmount how many disputes match the other filters, so "none over 500" can
     *                            still say how many there are and how large the biggest one is
     */
    public record DisputeList(String status, String minAmount, int count, String totalClaimed,
                              String largestClaimed, int countIgnoringAmount, String largestIgnoringAmount,
                              List<DisputeRow> disputes) {
    }

    public record IssueRow(Long resultId, String outcome, String trackingNumber, String expectedAmount,
                           String collectedAmount, String difference, boolean hasDispute) {
    }

    /**
     * @param flagged {@code true} when at least one line is not MATCHED
     */
    public record StatementExplanation(Long statementId, Long courierId, String periodStart, String periodEnd,
                                       String status, int lineCount, boolean flagged, Map<String, Long> counts,
                                       String expectedTotal, String collectedTotal, String feesTotal,
                                       String netRemitted, String differenceTotal, List<IssueRow> issues) {
    }

    public record StatementRow(Long id, Long courierId, String periodStart, String periodEnd, String status,
                               int lineCount, String uploadedAt) {
    }

    public record StatementList(int count, List<StatementRow> statements) {
    }

    public record OrderRow(Long id, String trackingNumber, String customerName, String city, String amountDue,
                           String status, String courierName) {
    }

    /** @param totalMatching all matching orders on the platform; {@code orders} holds at most 50 */
    public record OrderList(long totalMatching, int shown, String shownTotalAmount, List<OrderRow> orders) {
    }

    public record DocSnippet(String source, String title, String text, double score) {
    }

    public record DocSnippets(String query, List<DocSnippet> snippets) {
    }
}
