# About the Settlement Copilot

## What the copilot can do
It answers questions about the signed-in merchant's settlement data: courier balances and rankings,
open disputes and their amounts, why a statement was flagged, statement lists, order searches, and
how the rules work. It answers in English or French.

## What the copilot will not do
It never guesses. If the data is missing, it says so. It does not forecast future amounts, does not
answer questions unrelated to COD settlement, and never changes data: it cannot open, resolve or
delete anything.

## Where the numbers come from
Every number comes from the settlement platform's API, called with the user's own access token, so
the copilot sees exactly what the user would see in the Settlement Desk. Each answer lists its
sources and a grounding check verifies that every amount in the answer appears in that data.
