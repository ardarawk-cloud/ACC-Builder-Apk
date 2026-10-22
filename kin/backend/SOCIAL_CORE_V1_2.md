# KIN Social Core v1.2

Status: milestone candidate on `feat/kin-foundation-v1`. Keep PR #15 DRAFT and do not merge until physical two-device verification passes.

## Product invariants

- Navigation remains `HOME · PEOPLE · + · CHAT · ME`.
- Home is chronological and contains the signed-in user plus current KIN connections; it is not an algorithmic discovery feed.
- Private Relationship Notes stay only in Android Room and are never sent to the API.
- Circle names and membership stay local/private. For a Circle-audience post, Android resolves matching connected account IDs locally and sends only those IDs as the server-side selected audience.
- Listening uses Android Sharesheet input. NotificationListener/broad notification access remains forbidden.

## Social API added in v1.2

Connections and blocking:
- `DELETE /v1/connections/{username}`
- `GET /v1/blocks`
- `POST /v1/blocks/{username}`
- `DELETE /v1/blocks/{username}`

Posts/feed:
- `POST /v1/posts`
- `GET /v1/feed`
- `PATCH /v1/posts/{post_id}`
- `DELETE /v1/posts/{post_id}`

Post audiences:
- `public` — visible in Home to current connections.
- `friends` — visible in Home to current connections.
- `selected` — visible only to current connections whose user IDs were selected locally by the author.
- `only_me` — visible only to the author.

Direct chat:
- `GET /v1/chats/{username}/messages`
- `POST /v1/chats/{username}/messages`

Direct messaging requires a current accepted friendship and is unavailable while either side blocks the other.

## Android cache

Room remains the offline cache for profile, people, posts, and messages. Successful remote refreshes are server-authoritative for public connection/feed state. Local Circle links and Private Relationship Notes remain device-local.

## Milestone verification target

Two physical phones, two real accounts:
1. Login survives backend restart using the persistent Termux database launcher.
2. Search → friend request → accept → both show connection.
3. Assign one or more private Circles and save a Private Relationship Note.
4. Publish a friends post on phone A → phone B sees it chronologically.
5. Publish a Circle-audience post → only matching selected connection(s) see it.
6. Edit/delete own post.
7. Send a direct message A → B and verify persistence after refresh.
8. Remove friend; messaging becomes unavailable.
9. Reconnect, block/unblock, and verify connection/search behavior.
