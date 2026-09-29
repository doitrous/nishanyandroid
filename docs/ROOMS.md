# Cross-platform room media: inspected contract, not implemented

Website reference: `server/src/roomsRealtime.js`, `roomsSfu.js` (tree inventory), `src/lib/rooms/roomChannel.ts`, `roomVideo.ts`, `useRoomAudio.ts`, plus StudyRooms sources at the commit recorded in CONTRACTS.md. Native Android signaling and media are **missing** at this checkpoint.

## Signaling

- Upgrade `wss://nishany.com/api/rooms/ws?code=<encoded room code>`. The room code is not a credential.
- Existing handshake accepts the authenticated `nsid` cookie, a Bearer Authorization header, or native `nishany.bearer` subprotocol with token as its following protocol. Never put a token in a URL/log. Android's existing cookie session can be reused only after testing upgrade behavior against deployment.
- Join/membership and role must already be valid. Socket `hello` carries userId, roomId and SFU availability/ICE servers. Do not mistake successful upgrade for media readiness.
- Every request correlates through `requestId`; asynchronous presence, chat, producer-close, forced-mute and voice-reset messages are independent. Pending requests must fail on disconnect; reconnect creates fresh transports/consumers, not replayed IDs.

| Message | Fields / response |
|---|---|
| `sfu:rtpCapabilities` | Returns router RTP capabilities and ICE servers |
| `sfu:createTransport` | direction `send` or `recv`; returns transport parameters |
| `sfu:connectTransport` | transportId, dtlsParameters |
| `sfu:produce` | transportId, kind, source (`mic`, `camera`, `screen`), rtpParameters, audience; returns producerId/source |
| `sfu:producers` | Current allowed remote producer list |
| `sfu:consume` | transportId, producerId, rtpCapabilities; server membership/audience enforced |
| `sfu:resume` | consumerId, after native consumer is ready |
| `sfu:pause` | producerId, paused |
| `sfu:consumerPrefs` | consumerId, paused, spatialLayer, temporalLayer, priority |
| `sfu:closeProducer` | producerId |
| `sfu:close` | Closes this peer's SFU resources |

Server role `viewer` cannot produce. Seat/audience restrictions apply to consumption as well. New producer events include kind/source; missing kind in older messages defaults to audio in the website model. Remote screen share is received as video with source `screen`, not as a website iframe. Account switching must stop tracks, release capture/audio focus and close the old socket before the new account joins.

## Required Android implementation and acceptance

Choose and pin an Android-compatible mediasoup/libwebrtc binding only after checking server router capabilities, codecs, DTLS/ICE, ABI support, licensing and device compatibility. A plain peer-to-peer WebRTC client does not implement this SFU contract.

Implement signaling request lifetimes, auth expiry, membership/host restrictions, producer/consumer races, deafen/local playback state, mic mute, camera flip, speaker/earpiece/wired/Bluetooth routing, audio focus, permission denial, reconnect, peer leave and deterministic teardown. Foreground microphone/camera service behavior and background restrictions must be tested on supported Android versions. Incoming calls need a real invitation/notification contract; a visible room is not an incoming-call system. Screen broadcast needs explicit MediaProjection consent and lifecycle handling; do not claim it from screen-share reception.

Acceptance requires two owner-authorized accounts and an actual endpoint with SFU/TURN operational. Record separately:

1. JVM protocol fixtures and emulator signaling/permission checks.
2. Two Android peers with real media, including network changes and background transitions.
3. Android ↔ website bidirectional audio/video and website screen-share reception.
4. Android ↔ physical iPhone bidirectional audio/video and teardown; inspect the newer local iOS Rooms implementation first.
5. Simultaneous website/iOS/Android room behavior with correct permissions and no cross-account carryover.

No such live tests were run. The user's iOS calling work is explicitly **not proven**, and this document must never be used as passing media evidence.
