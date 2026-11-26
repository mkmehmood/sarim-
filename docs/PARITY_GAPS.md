# Web ↔ Android parity gap report (auto-generated)

Labels present in the web tab markup (index.html) that do not appear anywhere in the matching Android screen source. Dynamic/JS-rendered text is not included.


## tab-prod  (14 labels not found)

- calculating...
- gross weight kg
- container weight kg
- net production kg
- sale price per kg
- 0.00/kg
- 0.00/unit
- profit per kg
- { await recordentry }">save production
- daily
- weekly
- monthly
- yearly
- weight kg

## tab-sales  (16 labels not found)

- id —
- sales representative for calculator link
- quantity kg
- expired / damage qty kg
- returned qty kg
- amount received ₨
- mahmood
- asaan
- add cash sale to daily ledger cash in +
- { await savecustomertransaction }">save transaction
- 0 customers
- loading customers
- all times
- daily sales
- 0.00 kg
- cash incl. received

## tab-rep  (12 labels not found)

- mobile number
- quantity kg
- amount received ₨
- expired / damage qty kg
- transaction value
- { await savereptransaction }">save transaction
- cash sale |  collection |  credit sale
- weekly
- monthly
- yearly
- all time
- loading customers

## tab-factory  (11 labels not found)

- select a store...
- { await savefactoryproductionentry }">save factory production
- 0 kg
- profit
- per kg
- material ⇅
- total value ⇅
- loading inventory
- total profit
- average
- average extra

## tab-payments  (65 labels not found)

- transaction manager
- id —
- clear
- name/entity
- transaction type
- operating expense
- cash in +
- cash out −
- add to daily ledger
- save transaction
- economic health dashboard
- operating cash flow
- cash in hand details
- net sales cash direct customers
- net production cash
- representative collections
- external payments received
- external payments made
- operating expenses
- net cash in hand
- current assets
- cash in hand above
- raw materials inventory
- formula units inventory
- accounts receivable
- direct customer credits
- representative credits
- total receivables
- total current assets
- current liabilities
- accounts payable
- supplier payables raw material invoices
- entity payables payment entities
- total accounts payable
- total current liabilities
- total enterprise value
- financial ratios
- current ratio
- quick ratio
- cash ratio
- cash in hand tracker
- weekly
- monthly
- yearly
- all times
- net cash flow
- production value
- sales tab cash
- calculator cash
- payments in
- payments out
- net cash
- total outstanding credits
- sales tab credits
- calculator credits
- total credits
- credits awaiting collection
- unified records
- entities & balances
- this month
- this week
- all time
- loading records
- offline — changes saved locally
- 0 pending

## tab-calc  (53 labels not found)

- sales representative
- {await loadsalesdata;};};} this">
- comparison
- date range
- select dates
- product quantities
- total sold qty
- returned qty
- expired / damage qty
- net sold qty
- return stock to physical inventory
- mahmood
- * this will increase the store's stock level without creating a new production record.
- expired stock — added to chora raw material
- * expired product will be added to the chora raw material inventory. it will not be added to production stock.
- credit sales qty
- commission paid advance/paid
- collections & handover cash
- recovered credit
- physical cash received from rep
- gross commission earned
- add cash received, expenses and commission to daily ledger
- { await savetransaction }">save transaction
- { await loadsalesdata'week' }">week
- { await loadsalesdata'month' }">month
- { await loadsalesdata'year' }">year
- { await loadsalesdata'all' }">all time
- view chart for
- {await loadsalesdatacurrentcompmode;};}">
- net profit value
- gross revenue value
- quantity sold
- competition
- market composition
- metric comparison
- performance analytics for
- weekly
- monthly
- yearly
- all times
- select metric
- weight kg
- total value
- total cost
- net profit
- cash sales
- credit sales
- performance overview
- all time
- · market debt tracker
- total credit issued
- cash recovered
- calculator history
---
## Structural differences found (manual review)

- **Payments**: web is one scrolling page (Transaction Manager → Economic Health Dashboard → Cash In Hand Tracker →
  Unified Records → Payment History). Android still uses four sub-tabs (Pay/Exp, Entities, Health, History).
  The Transaction Manager form now matches the web (field order, labels, name search, type buttons, validation).
- **Economic Health numbers differ**: Android `FinanceEngine.calculateEconomicHealth` adds finished stock and entity
  receivables, uses a ledger-based cash figure, and has no quick/cash ratio. The web (`utilities-sales.js`
  `calculateNetCash`) derives cash from production/sales/calculator/payments inside the selected period.
- **Data model gaps blocking an exact port**: `SalesRecord.mergedSummary`, `PaymentTransaction.supplierCreditAmount`
  and `PaymentTransaction.category` exist on the web but not in `AppModels.kt`/`WorkspaceJsonCodec`.
- **Calculator**: 53 web labels have no Android counterpart (comparison/date-range section, product quantity panels, etc.).
- **Payments channel**: Android had a per-entry Channel picker that the web does not have; it is no longer shown (defaults to the first payment mode).
