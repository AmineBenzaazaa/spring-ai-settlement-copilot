# Courier balances and money rules

## Courier balance formulas
For one courier and one merchant:
- expectedCollections = sum of amountDue of the courier's DELIVERED orders
- reportedCollections = sum of amountCollected on reconciled statement lines linked to those orders
- fees = sum of the fees the courier kept on those lines
- netRemitted = reportedCollections - fees (what actually reached the merchant's bank)
- balanceOwed = expectedCollections - reportedCollections (cash the courier still has to report and pay)

## Reading a balance
A courier with no reconciled statement yet has reportedCollections 0.00, so its balanceOwed equals
everything it was expected to collect. A positive balanceOwed is money the merchant should chase.
The settlement overview sums these numbers over all couriers.

## Money rules
All amounts are in Moroccan dirham (MAD) with exactly two decimals. Amounts are stored as exact
decimals, never floating point. A courier file with more than two decimals (for example 10.005) is
rejected rather than rounded, because it is a data problem.

## Discrepancy amount
The overview's discrepancyAmount is the sum of |collected - expected| over all non-matched results
on reconciled statements. openDisputedAmount is the sum of the claimed amounts of OPEN disputes.
