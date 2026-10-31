# Papa Sauce Lab v33 — Boss Read-Only Web Dashboard

Public landing page: https://laporanpapa.nadmo.id/
Worker source: apps/papa-sauce-boss-report/worker.js
Worker name: papa-sauce-boss-report
Storage: Cloudflare KV PAPA_SAUCE_BOSS_REPORTS; latest aggregated report snapshot only.

SECURITY: Boss link requires long unguessable viewer token. Read-only API requires a SHA-256 verifier. Upload requires separate private publisher token, entered once inside Owner APK. Tokens never checked into GitHub. No personal customer names, raw expense notes, or detailed records exported.

OWNER FLOW: In APK v33, open Laporan > Dashboard Laporan Bos > Kirim Laporan / Bagikan Link Bos. Enter publisher and viewer codes once. Press KIRIM LAPORAN TERBARU. Share the private link only with boss.

IMPORTANT: Data are stored locally on the phone in IndexedDB and are NOT uploaded automatically. Boss sees data last uploaded by Owner, not live real-time inventory. Last published timestamp must always show. If historical records are missing or stock is negative, reconcile before relying on the figures.

REPORT CONTENT: Monthly production, stock out, stock balance by menu, daily stock card including opening balance, distribution breakdown PSK / PSP / PSB, LAB sales and expenses by category, daily activity.

DEVICE ACCEPTANCE: Install as an in-place signed APK update. Confirm existing October transactions survive. Complete one Owner publication and validate boss link on unrelated browser. Confirm wrong tokens cannot read or overwrite data and no default public endpoint leaks data. Check sync failure leaves local data untouched.

NOTE: Owner and viewer codes are provisioned outside repository; share only through a private channel. Revocation is done by replacing hash verifiers and re-publishing Worker.
