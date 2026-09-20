# Live Weather WebSocket — API Contract (v1)

Endpoint for real-time weather conditions, severe-weather alerts and air-quality changes.
This document is the contract between the Ktor server and any client (Android, iOS, Wear, web).

---

## 1. Connection

```
wss://data.androidplay.in/ws/live
```

The handshake is an ordinary authenticated HTTP GET carrying the WebSocket upgrade headers.
It is protected by the same `jwt-auth` provider as every REST route, so **an invalid token is
rejected with a plain HTTP 401 before the protocol upgrade happens.**

That ordering is deliberate and is worth understanding: the alternative — upgrading first and
then closing the socket — forces the client to distinguish "auth failed" from "network died"
by inspecting close codes, which is far more error-prone than reading a status code.

### Authentication

The token is read, in order:

1. `Authorization: Bearer <jwt>` request header — **use this from mobile**
2. `jwt_token` cookie — fallback for browsers

Native clients can set headers on a WebSocket handshake, so mobile should always use the header.
There is deliberately **no `?token=` query parameter**: URLs end up in access logs, proxy logs and
crash reports, and a JWT in a URL is a credential leak waiting to happen.

### Handshake example

```http
GET /ws/live HTTP/1.1
Host: data.androidplay.in
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: <base64 nonce>
Sec-WebSocket-Version: 13
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

```http
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: <derived>
```

### Transport parameters

| Parameter | Value | Why |
|---|---|---|
| Server ping period | 20s | Keeps carrier-NAT and load-balancer idle timers (often 60s) from silently reclaiming the flow |
| Pong timeout | 45s | ~2 missed pings; rides out a cell handover, reclaims a tunnelled phone quickly |
| Max inbound frame | 64 KB | Commands are a few hundred bytes; the cap stops a hostile client exhausting heap |
| Compression | off | permessage-deflate costs 30–300 KB of native memory per connection to compress ~120-byte payloads |

The 20s ping is handled by the WebSocket library at both ends. **The client does not need to
implement it**, but it must not disable it.

---

## 2. Message envelope

Every frame in both directions is a JSON **text** frame containing an object with a `type` field.
Binary frames are rejected.

Common fields:

| Field | Direction | Meaning |
|---|---|---|
| `type` | both | Discriminator. Switch on this. |
| `v` | server→client | Protocol version (currently `1`) |
| `cid` | client→server, echoed back | Client-generated correlation id |
| `seq` | server→client events | Per-topic monotonic sequence number |
| `ts` | server→client events | Server send time, epoch millis |

**Clients must ignore unknown `type` values and unknown fields.** New event kinds will be added
within v1. A client that crashes on an unrecognised frame cannot be shipped safely, because the
server will be upgraded long before every installed app is.

---

## 3. Client → Server commands

### 3.1 `subscribe_saved` — the normal path

Subscribes to every location in the user's `saved_locations`. Preferred, because the server
already has this list and the app doesn't have to resend it.

```json
{
  "type": "subscribe_saved",
  "cid": "c-1",
  "channels": ["conditions", "alerts", "aqi"],
  "cursor": { "loc:tuvz0": 41 }
}
```

| Field | Required | Notes |
|---|---|---|
| `channels` | no | Requested channels. Server intersects with entitlements; omitted means "everything I'm entitled to" |
| `cursor` | no | Last `seq` the client successfully processed per topic. Used for gap detection on reconnect |

### 3.2 `subscribe` — explicit coordinates

For the current GPS position or a location not yet saved.

```json
{
  "type": "subscribe",
  "cid": "c-2",
  "locations": [
    { "lat": 22.5726, "lon": 88.3639, "label": "Kolkata" },
    { "lat": 12.9716, "lon": 77.5946, "label": "Bengaluru" }
  ],
  "channels": ["conditions", "alerts"]
}
```

`label` is echoed back on the ack purely so the client can match a topic to its UI row.

> **Coordinates are quantised server-side** into ~4.9 km geohash cells. Two clients 200 m apart
> receive the *same* topic key. This is what keeps upstream API cost proportional to geography
> rather than to user count. The client must therefore use the `topic` from the ack as its key —
> never the coordinates it sent.

### 3.3 `unsubscribe`

```json
{ "type": "unsubscribe", "cid": "c-3", "topics": ["loc:tuvz0"] }
```

### 3.4 `ping`

```json
{ "type": "ping", "cid": "c-4", "clientTime": 1758182400000 }
```

Application-level liveness check, distinct from the transport ping. The transport ping is
answered by the networking library and proves only that the socket is open; this one traverses
the server's reader coroutine and therefore proves the server is still *processing*. Use it
when resuming from background before trusting an old connection.

---

## 4. Server → Client messages

### 4.1 `hello` — always the first frame

```json
{
  "type": "hello",
  "v": 1,
  "connectionId": "6f1c...",
  "serverTime": 1758182400000,
  "tier": "PREMIUM",
  "allowedChannels": ["conditions", "alerts", "aqi"],
  "cadenceSeconds": 15,
  "maxTopics": 20,
  "heartbeatSeconds": 30
}
```

The server tells the client how to behave rather than the client hardcoding it. Tier limits can
then change server-side without an app release. **Do not hardcode `maxTopics` or `cadenceSeconds`
in the app** — read them from `hello`.

### 4.2 `subscribed`

```json
{
  "type": "subscribed",
  "v": 1,
  "cid": "c-1",
  "topics": [
    { "topic": "loc:tuvz0", "label": "Kolkata", "lat": 22.5634, "lon": 88.3550, "seq": 41, "gap": false }
  ],
  "rejected": [
    { "label": "Nowhere", "lat": 99.0, "lon": 0.0, "code": "INVALID_COORDINATES", "message": "..." }
  ]
}
```

Partial success is normal: some topics accepted, others rejected. The client must render both.

`gap: true` means the client's cursor is older than the server can reconcile. The client must
**discard its cached state for that topic** and treat the next `snapshot` as a full reset rather
than applying it as a delta.

`lat`/`lon` in the ack are the **cell centre**, not what the client sent.

### 4.3 `event` — the payload

```json
{
  "type": "event",
  "v": 1,
  "topic": "loc:tuvz0",
  "seq": 42,
  "ts": 1758182400123,
  "event": { "kind": "conditions.update", "...": "..." }
}
```

Note the nested discriminator: the envelope uses `type`, the inner event uses `kind`.

#### Event kinds

| `kind` | Channel | Meaning |
|---|---|---|
| `snapshot` | conditions | Full current state. Sent on subscribe and after a gap. **Reset local state.** |
| `conditions.update` | conditions | Delta. `changed` lists which fields moved, so the UI animates only those |
| `alert.issued` | alerts | Severe weather alert became active |
| `alert.cleared` | alerts | Alert expired or was withdrawn |
| `aqi.threshold_crossed` | aqi | AQI moved between bands 1–5; `worsening` says which way |
| `wind.gust_spike` | conditions | Gust jumped ≥4 m/s in one interval |
| `rain.starting_soon` | conditions | Precipitation expected within 90 minutes |
| `uv.high` | conditions | UV index crossed into the high band (≥6) |

Examples:

```json
{ "kind": "snapshot",
  "conditions": { "observedAt": 1758182400, "tempC": 28.4, "feelsLikeC": 31.2, "humidity": 74,
                  "pressure": 1008, "windSpeedMs": 3.1, "windGustMs": 5.4, "uvi": 7.2,
                  "clouds": 40, "summary": "scattered clouds", "icon": "03d" },
  "aqi": { "band": 3, "pm25": 42.1, "pm10": 61.0 },
  "activeAlerts": [] }
```

```json
{ "kind": "conditions.update",
  "conditions": { "...": "..." },
  "changed": ["tempC", "windGustMs"] }
```

```json
{ "kind": "alert.issued",
  "alert": { "id": "1x9kf2", "event": "Thunderstorm Warning",
             "senderName": "India Meteorological Department",
             "description": "Severe thunderstorm...", "startsAt": 1758182000, "endsAt": 1758192000 } }
```

`alert.id` is a stable synthetic id (derived from sender + event + start time), because upstream
supplies none. The client uses it to match an `alert.issued` with its later `alert.cleared`.

### 4.4 `pong`

```json
{ "type": "pong", "v": 1, "cid": "c-4", "serverTime": 1758182400200, "clientTime": 1758182400000 }
```

`clientTime` is echoed so the client can compute round-trip time without keeping a pending map.

### 4.5 `error` — recoverable, connection stays open

```json
{ "type": "error", "v": 1, "cid": "c-2", "code": "TOPIC_LIMIT_EXCEEDED", "message": "..." }
```

| Code | Meaning |
|---|---|
| `BAD_FRAME` | Frame could not be parsed |
| `UNKNOWN_COMMAND` | Unrecognised `type` |
| `TOPIC_LIMIT_EXCEEDED` | Plan's `maxTopics` reached |
| `CHANNEL_FORBIDDEN` | Channel not in this tier |
| `INVALID_COORDINATES` | lat/lon out of range |
| `RATE_LIMITED` | Too many commands |
| `INTERNAL` | Server-side failure |

---

## 5. Close codes

Fatal problems close the socket. **The client's reconnect policy must branch on the code** —
blindly retrying on 4401 produces an infinite auth-failure loop, which is one of the most common
real-world WebSocket bugs.

| Code | Meaning | Client action |
|---|---|---|
| `1000` | Normal | Do not reconnect (client initiated) |
| `1001` | Server going away | Reconnect with backoff |
| `1011` | Internal error | Reconnect with backoff |
| `4400` | Unsupported protocol version | **Do not reconnect.** Prompt for app update |
| `4401` | Unauthorized | Refresh the JWT, then reconnect once |
| `4403` | Token expired | Refresh the JWT, then reconnect once |
| `4008` | Slow consumer — client couldn't keep up | Reconnect with backoff; expect a fresh `snapshot` |
| `4429` | Rate limited | Back off hard (≥60s) |
| `4503` | Server shutting down | Reconnect with backoff + jitter |

---

## 6. Entitlements

| | FREE | PREMIUM |
|---|---|---|
| Channels | `conditions`, `alerts` | `conditions`, `alerts`, `aqi` |
| Conditions cadence | 60s | 15s |
| Max live topics | 3 | 20 |

Alerts are **never** throttled or withheld, on any tier. Cadence is a product lever on
convenience data; it is not applied to safety data.

Entitlements are resolved once at connect and cached for the connection's lifetime. A user who
upgrades mid-session gets the new tier on their next reconnect.

---

## 7. What the client must implement

Server-side work is done; this is the checklist for the app team.

### 7.1 Connection lifecycle

1. Connect with `Authorization: Bearer <jwt>`
2. Wait for `hello`; store `maxTopics`, `cadenceSeconds`, `heartbeatSeconds`, `allowedChannels`
3. Send `subscribe_saved` (and `subscribe` for current GPS)
4. Handle `subscribed`: record each `topic`, its `seq`, and honour `gap`
5. Stream `event` frames into the UI
6. On close, branch on the close code (table above)

### 7.2 Reconnect policy

Exponential backoff **with jitter**: `min(30s, 1s × 2^attempt) ± 20% random`.

The jitter is not decoration. When a load balancer restarts, every client disconnects at the
same instant; without jitter they all retry at the same instant and the thundering herd knocks
the server over again. This is the single most common omission in client WebSocket code.

Reset the attempt counter only after a connection has been **stable** for ~30s, not on
connect — otherwise a connect/drop loop never backs off at all.

### 7.3 Sequence tracking and gap detection

Keep `lastSeq` per topic. On an `event`, if `seq > lastSeq + 1`, frames were missed (dropped
under backpressure, or lost across a reconnect). Two safe responses:

- resubscribe to that topic and take the fresh `snapshot`, or
- fall back to the existing `GET /weather` REST call once

Send your stored `lastSeq` values as `cursor` when resubscribing so the server can tell you
whether a gap occurred.

### 7.4 Lifecycle — and the part that is genuinely different per platform

**Android.** Disconnect when the last UI holder goes to background. A socket held open across
Doze is not "real time" — the OS suspends the process and the socket dies without telling you.
Reconnect on foreground, but **verify liveness first**: a socket that survived backgrounding may
be a black hole (the NAT mapping expired without either end being told). Send an app-level `ping`
and treat no `pong` within ~5s as dead. This is exactly why `ping`/`pong` exists in the protocol
rather than relying on the transport ping alone.

**iOS.** The system is stricter: sockets are torn down on background almost immediately.
Disconnect explicitly on `scenePhase` leaving foreground rather than waiting to be killed, so the
server reclaims the subscription and tears down the poller cleanly instead of waiting for a 45s
pong timeout.

**Wear.** Do not open a second socket. Wear should receive data relayed from the phone; a watch
holding its own WebSocket is a battery problem with no upside.

**Never** hold the socket open for background alert delivery. That is what FCM/APNs are for.
The WebSocket is a foreground-only optimisation.

### 7.5 Things that will bite

- The `topic` in the ack is **not** derived from the coordinates you sent — always key on what the server returned
- `snapshot` means **replace**, `conditions.update` means **merge**. Getting this backwards leaves stale fields on screen forever
- `changed` lets you animate only what moved; using it makes the UI feel materially better
- A `subscribed` reply can be partially rejected; render both lists
- Unknown `kind` values must be ignored, not crashed on

---

## 8. Development and load testing

Real upstream data changes roughly every 10 minutes, so nothing visibly "streams" against the
live vendor. For development:

```bash
LIVE_WEATHER_SIMULATION=true ./gradlew run
```

This swaps `UpstreamConditionsSource` for `SimulatedConditionsSource`, which seeds itself from
one real sample per location and then random-walks it every 2 seconds, injecting synthetic
severe-weather alerts roughly every 40 ticks. The socket layer is identical — only the data
source changes — so anything verified against the simulator holds against production.

**Never enable this in production.** Startup logs a warning when it is on.

Manual smoke test:

```bash
# get a token
TOKEN=$(curl -s -X POST https://data.androidplay.in/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"...","password":"..."}' | jq -r .data.token)

# connect (websocat)
websocat -H="Authorization: Bearer $TOKEN" wss://data.androidplay.in/ws/live
# then paste:
{"type":"subscribe_saved","cid":"c-1"}
```

---

## 9. Server architecture

```
                 ┌──────────────┐   subscribe/unsubscribe   ┌──────────────┐
  WebSocket ─────│ LiveWeather  │──────────────────────────▶│   LiveHub    │
   (reader)      │    Route     │                           │              │
                 └──────────────┘                           │  topic ──▶   │
                        │                                   │  subscribers │
                        │ offer()                           └──────┬───────┘
                        ▼                                          │ one poller
                 ┌──────────────┐    drain    ┌────────┐           │ per TOPIC
                 │ LiveConnection│───────────▶│ socket │           ▼
                 │  bounded queue│  (writer)  │ outgoing│   ┌──────────────────┐
                 └──────────────┘             └────────┘   │ ConditionsSource │
                                                           │  Upstream | Sim  │
                                                           └────────┬─────────┘
                                                                    ▼
                                                           ┌──────────────────┐
                                                           │ LiveEventDeriver │
                                                           │ snapshot ▶ events│
                                                           └──────────────────┘
```

Key properties:

- **One poller per topic, not per connection.** 500 users watching 40 cells cost 5,760 upstream
  calls/day instead of 72,000. Cost scales with geography, not with user count.
- **Fan-out never writes to a socket.** It offers to a bounded per-connection queue; a dedicated
  writer coroutine drains it. One slow phone cannot stall the other subscribers of its topic.
- **Lossy for telemetry, lossless for alerts.** A full queue drops `conditions.update` frames
  silently (a stale temperature has no value once a newer one exists) but a client that cannot
  accept alerts is disconnected with 4008 so it reconnects and re-syncs.
- **Idle pollers linger 60s** before teardown, so a phone that reconnects after a lift or a tunnel
  finds a warm poller instead of paying a cold start and an upstream call.

### Scaling past one instance

This implementation fans out **within a single JVM**. With more than one replica, a client
connected to pod A never sees events produced by pod B's poller.

`LiveHub.publish()` is the single, deliberate seam where that is fixed:

1. Elect one poller per topic cluster-wide with a Redis lock (`SET topic:lock NX PX`), renewed
   on each tick, so N pods don't produce N× the upstream calls
2. The elected poller publishes derived events to Redis channel `loc:{geohash}`
3. Every pod subscribes to the channels its local connections need and fans out locally

The sequence counter then moves to `INCR loc:{geohash}:seq` so `seq` stays globally monotonic —
without that, a client that reconnects to a different pod sees the sequence jump backwards and
would incorrectly report a gap.

Note this needs a dedicated thread for Jedis pub/sub (`JedisPubSub.subscribe` blocks), which is
why it is not wired in yet.
