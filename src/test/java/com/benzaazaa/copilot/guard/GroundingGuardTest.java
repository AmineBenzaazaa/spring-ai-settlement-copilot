package com.benzaazaa.copilot.guard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GroundingGuardTest {

    private final GroundingGuard guard = new GroundingGuard();
    private final List<String> payloads = List.of("{\"balanceOwed\":\"530.50\",\"expected\":\"4588.00\",\"total\":\"12204.00\"}",
            "{\"difference\":\"-50.00\"}");

    @Test
    void amountsCopiedFromTheDataAreGrounded() {
        GroundingGuard.Verification v = guard.check("Speedex owes **530.50 MAD** of 4588.00 MAD expected.", payloads);
        assertThat(v.grounded()).isTrue();
        assertThat(v.checkedAmounts()).isEqualTo(2);
        assertThat(v.ungrounded()).isEmpty();
    }

    @Test
    void inventedAmountsAreReported() {
        GroundingGuard.Verification v = guard.check("Speedex owes 531.00 MAD and Rapido 530.50 MAD", payloads);
        assertThat(v.grounded()).isFalse();
        assertThat(v.ungrounded()).containsExactly("531.00");
    }

    @Test
    void frenchFormattingAndThousandsSeparatorsAreNormalized() {
        assertThat(guard.check("Solde : 530,50 MAD, attendu 4 588,00 MAD, total 12 204,00 MAD", payloads).grounded()).isTrue();
        assertThat(guard.check("Total 12,204.00 MAD", payloads).grounded()).isTrue();
        assertThat(guard.check("Total 12 204,00 MAD", payloads).grounded()).isTrue();
    }

    @Test
    void signsAndZeroAmountsDoNotNeedEvidence() {
        assertThat(guard.check("difference -50.00 MAD, fees 0.00 MAD", payloads).grounded()).isTrue();
    }

    @Test
    void answersWithoutAmountsAreTriviallyGrounded() {
        GroundingGuard.Verification v = guard.check("I couldn't find statement #12.", List.of());
        assertThat(v.grounded()).isTrue();
        assertThat(v.checkedAmounts()).isZero();
        assertThat(guard.check(null, List.of()).grounded()).isTrue();
    }

    @Test
    void normalizeHandlesLeadingZerosAndSeparators() {
        assertThat(GroundingGuard.normalize("0.50")).isEqualTo("0.50");
        assertThat(GroundingGuard.normalize("1.234,50")).isEqualTo("1234.50");
        assertThat(GroundingGuard.extract("ids 12 and 2026-10-01 are not money")).isEmpty();
    }
}
