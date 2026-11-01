# Radio App – Developer API Documentation

Radio provides two interfaces for external applications and integrations. Depending on the platform and use case, developers can choose between the REST Remote Control API and the Android AIDL API.

## Available APIs

| API      | Platform     | Communication    |
|:---------|:-------------|:-----------------|
| REST API | Any platform | HTTP / WebSocket |
| AIDL API | Android      | IPC / Binder     |

---

## REST API – Remote Control

The REST API allows external clients to remotely control the Radio application over HTTP.

**Features:**
- Playback control (play, pause, resume, next, previous)
- Retrieve current playback status
- Access saved radio stations
- Retrieve station images
- Get the current audio stream URL
- Real-time updates via WebSockets
- Secure client authentication and QR code pairing

**Documentation:** [REST API Documentation](REST_API.md)

---

## AIDL API – Android IPC

Starting with version **15.2**, Radio provides an AIDL interface that allows external Android applications to communicate directly with the Radio playback service.

**Service information:**
- **Package:** `com.michatec.radio`
- **Service:** `com.michatec.radio.api.RadioApiService`
- **Interface:** `com.michatec.radio.api.IRadioApi`

**Features:**
- Play, pause and stop playback
- Check playback state
- Retrieve current station information
- Access track metadata
- Switch stations using their UUID
- Retrieve saved and favorite stations as JSON

**Documentation:** [AIDL API Documentation](AIDL_API.md)

---

## Which API Should I Use?

Choose the REST API if you want to:
- Build a web-based remote control.
- Integrate Radio with desktop applications.
- Control Radio over your local network.
- Develop integrations using HTTP or WebSockets.

Choose the AIDL API if you want to:
- Integrate Radio into another Android application.
- Communicate directly with the Android playback service.
- Control playback through Android IPC without HTTP requests.

---

## Version Compatibility

| API      | Minimum Radio Version |
|:---------|:----------------------|
| REST API | 15.1                  |
| AIDL API | 15.2                  |

---

## Package Information

- **Application:** Radio
- **Package:** `com.michatec.radio`
- **License:** GPL-3.0