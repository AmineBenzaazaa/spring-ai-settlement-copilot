# Disputes

## Opening a dispute
A merchant can open a dispute on any reconciliation result that is not MATCHED. The claimed amount
defaults to the full discrepancy |collected - expected| and can be lowered. One result can have only
one dispute.

## Dispute lifecycle
A dispute starts OPEN. Finance operations staff (STAFF role) resolve it: RESOLVED means the courier
pays the claimed amount, REJECTED means the claim is refused. Merchants cannot resolve their own
disputes. Resolution uses optimistic locking, so two staff members cannot resolve the same dispute twice.

## Export
Disputes can be exported as CSV. The export protects against spreadsheet formula injection by
escaping cells that start with =, +, - or @.

## Tenant isolation
A merchant only ever sees its own statements, disputes and orders. Asking for another merchant's
statement returns "not found", exactly as if it did not exist. Staff users can see all merchants.
