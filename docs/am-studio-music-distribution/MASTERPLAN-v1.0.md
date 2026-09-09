# AM STUDIO Music Distribution — MASTERPLAN v1.0

Status: FOUNDATION / ACTIVE BUILD  
Date: 2026-09-09  
Owner / final authority: Arda  
Brand: AM STUDIO

## 1. North-star objective
Bangun platform distribusi musik Indonesia-first tempat artis/label dapat upload satu release, lolos rights/QC, memilih DSP, didistribusikan melalui provider layer, melacak status delivery dan royalti, lalu meminta payout. Arsitektur wajib memungkinkan provider white-label/API diganti atau dicampur dengan direct DSP/DDEX tanpa membangun ulang client.

## 2. Product promise
**Upload once → validate → distribute → monetize → account royalties → payout.**

APK bukan aplikasi yang menyimpan credential Spotify/TikTok atau menembak DSP secara langsung. APK adalah client milik AM STUDIO yang hanya berbicara ke AM STUDIO Backend API.

## 3. Non-negotiable architecture rules
1. Provider API secret tidak pernah masuk APK.
2. APK hanya memanggil AM STUDIO Backend.
3. Provider distribusi selalu berada di balik ProviderAdapter.
4. AM STUDIO memiliki canonical catalog ID dan release state sendiri.
5. Provider/DSP ID hanya external reference.
6. Royalty ledger immutable; koreksi memakai adjustment entry, bukan edit diam-diam.
7. Payout tidak boleh jalan sebelum KYC/tax/payment validation.
8. Rights declaration dan anti-fraud adalah blocking gate.
9. Setiap perubahan release memiliki version/audit history.
10. Production signing/package identity dipisahkan dari debug builder identity.

## 4. Business stages
### Phase 0 — Foundation
- Brand + APK shell.
- Canonical data model.
- Release wizard + QC state machine.
- Provider adapter contract.
- Royalty/payout domain model.
- Compliance/audit requirements.

### Phase 1 — Private catalog pilot
- AM STUDIO + artis undangan.
- Connect 1 provider sandbox; target awal LabelGrid Engine, subject to commercial approval.
- Release create/update, signed asset upload, validation, submit, webhook status.
- Admin manual QC fallback.
- Belum ada public signup.

### Phase 2 — Closed artist beta
- Artist/team/label accounts.
- KYC onboarding.
- Plan/commission rules.
- Update/takedown flow.
- Royalty statement ingestion + artist balance.
- Controlled payout.

### Phase 3 — Public distributor
- Self-service onboarding.
- Billing/subscription.
- Automated risk/content checks.
- Support/dispute/takedown workflow.
- Tax invoice/payout reporting.
- Multi-provider redundancy bila memungkinkan.

### Phase 4 — Label operating system
- Multi-label organizations.
- Team roles/approvals.
- Bulk import.
- Royalty splits/collaborators.
- Advanced analytics/smart links.
- Partner API.

### Phase 5 — Scale & anti-fraud
- Fingerprinting/duplicate detection.
- Streaming anomaly scoring.
- Release/payout risk holds.
- Rights complaint/takedown SLA.
- Financial/provider reconciliation.

### Phase 6 — Hybrid direct distribution
- Selected DSP bisa dirutekan lewat direct agreement; DSP lain tetap white-label.
- DDEX ERN delivery.
- Usage/statement ingestion dan reconciliation.
- Direct DSP certification/testing sesuai requirement.

### Phase 7 — Direct aggregator target
- Direct commercial agreements dengan selected DSPs.
- DDEX-native delivery/reporting at scale.
- Own delivery monitoring, rights/fraud ops, royalty accounting dan partner API.
- White-label provider hanya fallback opsional.

## 5. User roles
- ARTIST: mengelola release miliknya dan melihat earnings.
- LABEL_ADMIN: artis, release, team, splits, statements.
- AM_QC: review release blocked/manual-review.
- AM_FINANCE: statements, holds, adjustments, payouts.
- AM_SUPPORT: release issue/takedown/copyright case.
- AM_ADMIN: provider routing, plans, DSP capability, policies.

## 6. Canonical release lifecycle
`DRAFT → PREFLIGHT → RIGHTS_CONFIRMED → READY_FOR_QC → QC_REVIEW → APPROVED → QUEUED_FOR_DELIVERY → DELIVERING → DSP_PROCESSING → LIVE`

Side states:
- NEEDS_CHANGES
- REJECTED
- DELIVERY_FAILED
- PARTIALLY_LIVE
- UPDATE_PENDING
- TAKEDOWN_PENDING
- TAKEN_DOWN
- FROZEN_RISK_REVIEW

Status provider harus dimapping ke state AM STUDIO; wording provider tidak boleh menjadi source of truth produk.

## 7. Core release workflow
1. Create release.
2. Isi metadata.
3. Tambah tracks/contributor credits.
4. Upload master + artwork via signed URL.
5. Deklarasi master/composition rights.
6. Pilih DSP/territories + release date.
7. Automated preflight.
8. Manual QC bila perlu.
9. Submit via DistributionProvider adapter.
10. Terima webhook/provider status.
11. Normalize status per DSP.
12. Surface live links/issues.
13. Controlled update/takedown dengan versioning.

## 8. Monetization model
Economics configurable per account/plan dan tidak hard-coded ke release logic.

Model yang didukung:
- subscription fee;
- per-release fee;
- revenue share;
- hybrid subscription + revenue share;
- negotiated label contract.

Foundation UI boleh memakai contoh **10% AM STUDIO / 90% artist**, tetapi hanya default demo configurable, bukan janji komersial final.

## 9. Royalty accounting
`Provider/DSP statement → import → normalize → track/release match → gross ledger → fees/withholding/commission → beneficiary split → available balance → payout batch`

Wajib simpan:
- source statement ID/hash;
- DSP, territory, usage period;
- currency + FX basis;
- ISRC/UPC mapping;
- gross/net distinction;
- commission rule version;
- adjustment trail;
- hold reason;
- payout batch ID.

## 10. Payout lifecycle
`PAYOUT_REQUESTED → VALIDATING → ON_HOLD / APPROVED → PROCESSING → PAID / FAILED / REVERSED`

Hard gates:
- verified identity/business profile;
- payment destination verification;
- minimum threshold;
- available—not pending—balance;
- no fraud/copyright hold;
- finance/audit rules.

## 11. Compliance & abuse controls
- Terms requiring authorization for master/composition.
- Copyright complaint/takedown process.
- Repeat-abuse policy.
- Artificial streaming prohibition.
- Payout/fraud/sanctions screening appropriate to provider/jurisdiction.
- Privacy/data-retention policy.
- Age/contracting requirements.
- Audit log for sensitive admin action.
- Content ID only when rights eligibility is proven.

## 12. Provider strategy
### Phase-1 target
LabelGrid Engine is current preferred evaluation because its public docs describe REST API, sandbox, release/assets/distribution, webhooks and royalty capabilities. Production requires accepted commercial agreement.

### Alternatives
- SonoSuite: mature white-label SaaS / broad DSP network, currently positioned more toward larger operations.
- Revelator: enterprise distribution/accounting APIs and ability to use own DSP agreements.

### ProviderAdapter contract
Every provider implementation must expose equivalent operations:
- createRelease
- updateRelease
- obtainUploadTarget / uploadAsset
- validateRelease
- submitRelease
- getReleaseStatus
- requestUpdate
- requestTakedown
- ingestWebhook
- fetchStatements
- fetchAnalytics

No client screen may call provider-specific endpoints directly.

## 13. Technical stack target
### Android client
- Foundation v1: Capacitor-compatible mobile WebView shell to validate core flow quickly.
- Production may remain Capacitor or migrate selected modules to Kotlin as runtime/security needs demand.
- Secure session/token storage must use native keystore in production.

### Backend
Start as modular monolith:
- Auth
- Organizations/Artists
- Catalog/Releases
- Assets
- QC/Rights
- Distribution Orchestrator
- Provider Adapters
- Webhooks
- Analytics
- Royalty Ledger
- Payouts
- Billing/Plans
- Admin/Audit

Split into services only after operational evidence justifies it.

### Data/infra
- PostgreSQL canonical DB.
- Object storage for audio/artwork.
- Job queue for delivery/webhooks/imports.
- Redis optional for idempotency/cache/jobs.
- Analytics warehouse later; never transactional source of truth.

## 14. Core entities
User, Organization, Membership, Artist, Label, Release, ReleaseVersion, Track, Contributor, Asset, RightsDeclaration, DSP, Territory, DistributionIntent, DistributionRequest, ProviderReference, DeliveryEvent, RoyaltyStatement, RoyaltyLine, LedgerEntry, SplitRule, Balance, PayoutAccount, Payout, RiskCase, CopyrightCase, AuditEvent, Plan, Subscription.

## 15. Security
- No provider secret in APK.
- Server-side RBAC.
- Signed uploads with size/type rules.
- Webhook signature verification.
- Idempotency keys for submission and payout.
- Encryption in transit/at rest.
- Separate staging/production provider credentials.
- Audit trail for admin/finance actions.

## 16. Client-facing API boundary
Versioned under `/v1`:
- `/auth`
- `/me`
- `/organizations`
- `/artists`
- `/releases`
- `/tracks`
- `/assets/uploads`
- `/qc`
- `/distribution`
- `/analytics`
- `/royalties`
- `/payouts`
- `/billing`
- `/support`

Client never receives raw provider credentials.

## 17. Foundation v1 APK scope
Implemented/targeted now:
- AM STUDIO branded mobile shell.
- Home/dashboard.
- Catalog.
- 5-step release wizard.
- Local audio/cover picker.
- Metadata + credits.
- Mandatory rights declarations.
- DSP intent independent from provider.
- Blocking preflight QC.
- Sandbox submission state.
- Royalty dashboard model.
- Payout gating.
- Provider/account page.
- Local persistent demo catalog.

Intentionally backend-gated, not faked:
- provider credentials;
- server asset upload;
- real DSP delivery;
- real KYC/payment processing;
- live royalty statement ingestion.

## 18. Definition of done
### Foundation
Core domain flow works, Android touch/buttons work, local data persists, APK builds reproducibly, masterplan checked into source.

### Pilot
One real test release can be submitted to provider sandbox and provider status returns into canonical AM STUDIO state.

### Closed beta
Invited artist can onboard, submit compliant release, see real DSP status/royalty data and complete controlled payout.

### Public launch
Billing, support, KYC, anti-fraud, copyright/takedown and financial reconciliation production-ready.

### Aggregator target
Selected DSP deliveries + statement flows can route through AM STUDIO direct agreements/DDEX without client redesign.

## 19. Forbidden shortcuts
- Never place LabelGrid/SonoSuite/Revelator secret in APK.
- Never use provider DB as AM STUDIO source of truth.
- Never skip rights declarations.
- Never represent payout as one mutable number.
- Never mark LIVE from UI assumption.
- Never claim monetization before provider/DSP acceptance and actual usage.
- Never bypass audit/version history.
- Never hard-code one provider into data model/navigation.

## 20. Immediate execution order
1. Build + device-test Foundation v1 APK.
2. Freeze canonical release/status/data model after QC.
3. Create AM STUDIO backend repo/environment.
4. Implement auth + releases + signed assets + ProviderAdapter interface.
5. Connect selected provider sandbox.
6. Complete one end-to-end sandbox release.
7. Add webhook normalization + admin QC.
8. Add statement ingestion + immutable ledger before any real payout.
9. Add KYC/payment rails + closed beta.
10. Only after operating evidence: public onboarding and scale.
