# AM STUDIO Music Distribution — API Contract v1

Rule: mobile app hanya bicara ke AM STUDIO Backend. Provider API server-side only.

## Create release
`POST /v1/releases`
```json
{
  "title": "Midnight Drive",
  "primary_artist_id": "art_...",
  "type": "single",
  "genre": "electronic",
  "language": "id",
  "release_date": "2026-10-01"
}
```

## Signed asset upload
`POST /v1/assets/uploads`
```json
{"kind":"audio_master","filename":"track.wav","content_type":"audio/wav","size":12345}
```
Response memberi canonical asset ID + signed upload URL. APK upload bytes ke object storage tanpa mengetahui provider secret.

## Rights declaration
`PUT /v1/releases/{release_id}/rights`
```json
{
  "master_control": true,
  "composition_permission": true,
  "no_artificial_streaming": true,
  "rights_holder": "AM STUDIO"
}
```

## Preflight
`POST /v1/releases/{release_id}/preflight`
```json
{"blocking":[],"warnings":[],"ready":true}
```

## Distribution intent
`PUT /v1/releases/{release_id}/distribution-intent`
```json
{
  "dsps": ["tiktok","spotify","apple_music","youtube_music"],
  "territories": ["WORLDWIDE"]
}
```

## Submit
`POST /v1/releases/{release_id}/submit`
Header wajib: `Idempotency-Key: <uuid>`

Response mengembalikan AM STUDIO distribution request ID. Provider IDs tetap external/server-side references.

## Distribution status
`GET /v1/releases/{release_id}/distribution`

Return canonical AM STUDIO state + per-DSP normalized status/live links.

## Royalty balance
`GET /v1/royalties/balance`

Return pending, available, held, lifetime values per currency.

## Payout
`POST /v1/payouts`

Requires verified payout profile, eligible available balance, no active hold, idempotency key.

## ProviderAdapter — server contract
```ts
interface DistributionProvider {
  createRelease(input: CanonicalRelease): Promise<ProviderReleaseRef>;
  validateRelease(ref: ProviderReleaseRef): Promise<ValidationResult>;
  submitRelease(ref: ProviderReleaseRef, intent: DistributionIntent): Promise<ProviderSubmissionRef>;
  getReleaseStatus(ref: ProviderReleaseRef): Promise<ProviderStatusSnapshot>;
  requestUpdate(ref: ProviderReleaseRef, version: CanonicalReleaseVersion): Promise<void>;
  requestTakedown(ref: ProviderReleaseRef, reason: string): Promise<void>;
  ingestWebhook(headers: Record<string,string>, body: unknown): Promise<ProviderEvent[]>;
  fetchStatements(window: StatementWindow): Promise<ProviderStatement[]>;
}
```

## Canonical status rule
Provider-specific status must be translated into AM STUDIO states. Client never treats a provider state string as the source of truth.

## Royalty rule
Statement lines are normalized into immutable ledger entries. Corrections create adjustment entries; historical ledger rows are not silently overwritten.
