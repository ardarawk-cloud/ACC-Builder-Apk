# Papa Sauce Lab v32 — Kartu Stok Bulanan

Purpose: replace repetitive manual physical stock card entry.

Columns in the monthly stock card: Tgl | Masuk (Produksi) | Keluar | Sisa. No bon number, no sender field.

Select a product and month, then view every calendar day in that month with daily totals and running balance. The opening balance carries all active stock movements before the first day of the selected month. Edited/voided transactions are excluded and the balance recalculates when opened.

Owner can copy the complete card to clipboard or share as text without any additional stock entry.

Example October ledger regression: opening 12, Oct04 out 12, Oct19 in 8/out 8, Oct22 in 8, Oct23 out 8 -> closing 0. Also tests correction exclusion and November carry forward. All twelve source-level checks passed.

Known limitation: history not yet entered into the local APK cannot be reconstructed from a physical card photo automatically. The card is based on recorded transactions; negative balances remain visible with an audit warning. Real Android share and clipboard support need device QC.

Release version 32; must retain canonical package ID and signing certificate. Do not uninstall the existing app or clear its data.
