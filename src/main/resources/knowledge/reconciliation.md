# How reconciliation works

## What a statement is
A remittance statement is one CSV file sent by one courier, for one merchant, covering one period
(for example Speedex Colis, 2026-10-01 to 2026-10-31). Each line says: tracking number, amount the
courier collected in cash at the door, and the fee the courier kept. Period boundaries use Moroccan
business days (Africa/Casablanca time zone).

## How each line is classified
Every CSV line is compared with the merchant's orders for that courier.
- MATCHED: an order with this tracking number exists and the collected amount equals the expected amount.
- AMOUNT_MISMATCH: the order exists but collected differs from expected. difference = collected - expected,
  so a negative difference means the courier short-paid the merchant.
- UNKNOWN_TRACKING: the tracking number does not belong to any of the merchant's orders with this courier.
  The courier reported cash for a parcel the merchant does not know.
- MISSING_FROM_STATEMENT: an order was DELIVERED during the period but is on no statement at all.
  The courier collected the cash but did not report it.

## Expected amount rule
expected = amountDue when the order is DELIVERED, otherwise 0. A returned parcel (refused by the
customer) collects nothing, so the courier should report 0.00 for it, usually with a smaller return fee.

## Why a statement is flagged
A statement is flagged as soon as at least one line is not MATCHED. The reconciliation report lists
the counts per outcome, the totals (expected, collected, fees, net remitted, difference) and each
discrepancy with its expected amount, collected amount and difference.

## Processing pipeline
Uploading a CSV validates every row synchronously (all row errors are reported at once), stores the
file, then reconciles it asynchronously after the database commit. Status goes UPLOADED, PROCESSING,
then RECONCILED (or FAILED). Uploading byte-identical content again is detected by its SHA-256 hash
and returns the existing statement instead of creating a duplicate.

## Re-running reconciliation
Reconciliation is idempotent: running it twice gives the same result. Staff can re-run a statement,
but not once one of its results is under dispute, so dispute evidence is never destroyed.
