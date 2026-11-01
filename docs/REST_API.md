# Radio App - Remote Control API Documentation

The Radio app provides a REST API to control playback and query information.

## Authentication

If authentication is enabled in the app settings, you must include the pairing code in your requests:

- **HTTP Headers:** Add `X-Remote-Key: YOUR_CODE`
- **Query Parameter:** Add `?token=YOUR_CODE` (fallback for images and WebSockets)

## Pairing via QR Code

Instead of typing the pairing code manually, the client can display a QR code that you confirm by scanning it with the Radio app. This is the recommended flow for desktop browsers and touch devices.

1. The client generates a random `clientId` and a random `pairKey` (32 bytes) and keeps both to itself.
2. The client calls `POST /api/pair/start?clientId=YOUR_CLIENT_ID` with the header `X-Pair-Key: YOUR_PAIR_KEY` to open a short-lived pairing session.
3. The client calls `GET /api/qr?clientId=YOUR_CLIENT_ID` with the same header to obtain a PNG QR code containing `radio-pair:PAIRING_CODE`. The pairing code is a one time value of the session – it is **not** the secret of your Radio app.
4. Open **Settings → Remote Control → Verify with QR Code** in the Radio app and scan the displayed code. The app approves that single session.
5. The client polls `GET /api/pair/status?clientId=YOUR_CLIENT_ID` with the same header until it returns `paired: true`, then stores the returned token and sends it as `X-Remote-Key` on all further requests.

The pairing endpoints are exempt from the pairing code check, so they can be called before a client is approved. A session is only readable with its `X-Pair-Key`, expires after 15 minutes and is dropped as soon as the token was handed out, so knowing a `clientId` alone is not enough.

### Example Usage (JavaScript)

```js
const clientId = crypto.randomUUID();
const pairKey = 'random 32 byte hex string';
const pairHeaders = { 'X-Pair-Key': pairKey };

await fetch('/api/pair/start?clientId=' + encodeURIComponent(clientId), { method: 'POST', headers: pairHeaders });
const qr = await fetch('/api/qr?clientId=' + encodeURIComponent(clientId), { headers: pairHeaders });
document.getElementById('qr').src = URL.createObjectURL(await qr.blob());

const poll = setInterval(async () => {
    const res = await fetch('/api/pair/status?clientId=' + encodeURIComponent(clientId), { headers: pairHeaders });
    if (!res.ok) return;
    const data = await res.json();
    if (data.paired && data.token) {
        clearInterval(poll);
        localStorage.setItem('radio-token', data.token);
    }
}, 2000);
```

## API Reference

### `GET /api/status`
Returns current playback status. Recommended for initial state only; use WebSockets for real-time updates.

**Response Example:**

```json
{
  "isPlaying": true,
  "playWhenReady": true,
  "playbackState": 3,
  "currentStationUuid": "fc080549-...",
  "metadata": "Artist - Title",
  "starred": true
}
```

### `WS /api/updates`
WebSocket endpoint for real-time status updates. Pushes status objects whenever state or metadata changes.

**Example Usage (JavaScript):**

```js
const token = '123456'; // From app settings
const socket = new WebSocket('ws://' + location.host + '/api/updates?token=' + encodeURIComponent(token));
socket.onmessage = (event) => {
  const status = JSON.parse(event.data);
  console.log('Update received:', status);
};
```

### `GET /api/stations`
Returns all stations in collection.

**Response Example:**

```json
[
  {
    "uuid": "fc080549-...",
    "name": "Station Name",
    "hasImage": true,
    "starred": true
  }
]
```

### `GET /api/image/{uuid}`
Returns the image for a specific station. Supports HTTP caching via `ETag` and `If-None-Match`.

**Response:** Image (binary data, JPEG)

### `GET /api/config`
Returns the app version.

**Response Example:**

```json
{ "version": "15.1" }
```

### `GET /api/stream`
Returns the actual live audio stream URI of the currently playing radio station.

**Response Example:**

```json
{ "status": "http://stream-url.example.com/live.mp3" }
```

### `POST /api/play/{uuid}`
Starts playback of a specific station.

**Response Example:**

```json
{ "status": "ok" }
```

### `POST /api/pause`
Pauses current playback.

**Response Example:**

```json
{ "status": "ok" }
```

### `POST /api/resume`
Resumes current playback.

**Response Example:**

```json
{ "status": "ok" }
```

### `POST /api/next`
Plays the next station.

**Response Example:**

```json
{ "status": "ok" }
```

### `POST /api/prev`
Plays the previous station.

**Response Example:**

```json
{ "status": "ok" }
```
