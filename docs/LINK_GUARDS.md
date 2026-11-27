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

## Not yet ported
- Stacked-collection check (a newer collection applied to the same sale) - Android stores `collectionAllocs` as an unordered map, so ordering needs a model change.
- Recovery-side guards/relinking, rename cascade, edit-time link issues (`getEditLinkIssue`, old-debt, settle toggle).
