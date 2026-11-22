package com.benzaazaa.copilot.chat;

import java.math.BigDecimal;
import java.util.List;

/**
 * A chart description the UI renders with Chart.js. Built by the server from tool data (never
 * by the model), so the chart can never disagree with the numbers in the answer.
 *
 * @param type bar or doughnut
 */
public record ChartSpec(String type, String title, List<String> labels, List<Dataset> datasets, String unit) {

    public record Dataset(String label, List<BigDecimal> data) {
    }
}
