# Delete link guards (parity with web `link-guards.js`)

`domain/LinkGuards.kt` blocks deletes that would leave the books inconsistent. It is enforced in
`SarimViewModel.softDeleteRecord` (single choke point, shows an error toast) and pre-checked via
`vm.deleteBlockReason(...)` in the payment/expense detail sheets so the reason shows before the confirm dialog.

| Record | Blocked when |
|---|---|
| Payment (`transactions`) | merged opening balance; or removing a received (IN) payment would push cash in hand below zero |
| Expense | any linked payment is a merged opening balance; or reversing linked IN payments would push cash below zero |
| Entity | has merged opening-balance records; or deleting its payment history would push cash below zero |
| Customer sale (`SalesAnalyticsEngine.getSaleBlockReason`) | (new) it still has linked payment records (`relatedSaleId`) - delete those first |

"Cash in hand" is `FinanceEngine.calculateEconomicHealth(snapshot, "all").cashInHand`, the number shown on the Payments dashboard.

## Recycle-bin recovery guards (`domain/RecoveryGuards.kt`)
Checked in `SarimViewModel.restoreTombstone` (error toast, nothing restored when blocked):

| Record | Recovery blocked when |
|---|---|
| Rep stock transfer sale | created by a calculator record - re-enter the calculation |
| Production return / return log | created by a calculator record (deletes made from the Production tab are stamped `_deleteOrigin=prod-tab` and stay recoverable; the marker is stripped on restore) |
| Partial-payment child (sales/rep) | parent sale missing, no longer CREDIT, or recovering would exceed the sale value |
| Collection (sales/rep) | a sale it paid is missing or already settled |
| Material (inventory) | its supplier entity is gone |
| Payment | its entity is gone; the other side of a transfer was permanently deleted; or putting a cash OUT back would push cash below zero |

## Not yet ported
- Stacked-collection check (a newer collection applied to the same sale) - Android stores `collectionAllocs` as an unordered map, so ordering needs a model change.
- Recovery re-linking (re-attach parent partial payments / re-apply collections, ID remap, stock re-deduction, CHORA re-add), calculator restore planning, by-date stock overdraw check, rename cascade, edit-time link issues (`getEditLinkIssue`, old-debt, settle toggle).
