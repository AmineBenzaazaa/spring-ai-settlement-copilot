package com.benzaazaa.copilot.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Client-side view of the settlement platform's JSON. Only the fields the copilot uses; unknown
 * fields are ignored, so the platform can add fields without breaking the copilot. Money arrives
 * as decimal strings ({@code "530.50"}) and is read as {@link BigDecimal}, never as a double.
 */
public final class SettlementModels {

    private SettlementModels() {
    }

    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record Overview(Long merchantId, String currency, BigDecimal expectedCollections,
                           BigDecimal reportedCollections, BigDecimal fees, BigDecimal netRemitted,
                           BigDecimal balanceOwed, long openDisputes, BigDecimal openDisputedAmount,
                           long discrepancies, BigDecimal discrepancyAmount, List<CourierPosition> couriers) {
    }

    public record CourierPosition(Long courierId, String name, String code, boolean active, long deliveredOrders,
                                  BigDecimal expectedCollections, BigDecimal reportedCollections, BigDecimal fees,
                                  BigDecimal netRemitted, BigDecimal balanceOwed) {
    }

    public record Courier(Long id, String name, String code, boolean active) {
    }

    public record CourierBalance(Long courierId, Long merchantId, String currency, long deliveredOrders,
                                 BigDecimal expectedCollections, BigDecimal reportedCollections, BigDecimal fees,
                                 BigDecimal netRemitted, BigDecimal balanceOwed) {
    }

    public record Dispute(Long id, Long resultId, Long statementId, String trackingNumber, String outcome,
                          Long merchantId, String reason, BigDecimal claimedAmount, String status,
                          String resolutionNote, Instant createdAt, Instant resolvedAt) {
    }

    public record Statement(Long id, Long merchantId, Long courierId, LocalDate periodStart, LocalDate periodEnd,
                            String originalFilename, String currency, String status, String errorMessage,
                            int lineCount, Instant uploadedAt, Instant reconciledAt) {
    }

    public record ReconciliationReport(Statement statement, Summary summary, List<Result> results) {
    }

    public record Summary(Map<String, Long> counts, BigDecimal expectedTotal, BigDecimal collectedTotal,
                          BigDecimal feesTotal, BigDecimal netRemitted, BigDecimal differenceTotal) {
    }

    public record Result(Long id, String outcome, String trackingNumber, Long orderId, Line line,
                         BigDecimal expectedAmount, BigDecimal collectedAmount, BigDecimal difference,
                         boolean hasDispute) {
    }

    public record Line(int rowNumber, String trackingNumber, BigDecimal amountCollected, BigDecimal fee) {
    }

    public record Order(Long id, Long courierId, String courierName, String trackingNumber, String customerName,
                        String city, BigDecimal amountDue, String currency, String status, Instant deliveredAt) {
    }
}
