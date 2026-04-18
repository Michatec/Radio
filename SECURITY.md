# Security Policy

## Reporting a Vulnerability

We take the security of Radio seriously. If you believe you have found a security vulnerability, please report it via **GitHub Issues** before disclosing it publicly.

**Create a new security issue**: [GitHub Issues — Security](https://github.com/michatec/Radio/issues/new?labels=security). We will respond within 48 hours acknowledging your report and work with you to understand and address the issue.

Include in your report:
- Description of the vulnerability
- Steps to reproduce
- Affected versions (if known)
- Potential impact
- Any suggested fixes (optional)

We appreciate responsible disclosure and will credit researchers who report valid security issues (unless you prefer to remain anonymous).

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 14.5    | :white_check_mark: |
| < 14.5  | :x:                |

Only the latest stable release receives security updates. Users on older versions are encouraged to update.

## Security Considerations

### Network Security
Radio streams audio over the internet and connects to the [radio-browser.info](https://www.radio-browser.info/) API for station search. The app allows cleartext HTTP traffic for radio stream compatibility (required for many legacy radio stations).

### Data Collection
- **Station data** (names, images, stream URLs) is fetched from radio-browser.info's public API
- **Station lists** are stored locally on the device only
- **No personal data** is collected or transmitted to michatec servers
- **Usage data** is not collected

### Permissions
Radio requests only the permissions necessary for core functionality:
- `INTERNET` — stream radio and fetch station metadata
- `ACCESS_NETWORK_STATE` — detect connectivity changes
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` — maintain playback when app is backgrounded
- `WAKE_LOCK` — prevent device from sleeping during playback

### Third-Party Dependencies
Radio uses several third-party libraries. Security issues in dependencies are monitored via Renovate bot for updates. Key dependencies include:
- AndroidX Media3 / ExoPlayer (media playback)
- Google Cast SDK (Chromecast support)
- Volley (HTTP requests)

### File Handling
The app can import M3U/PLS playlist files from external sources. Files are processed locally and stream URLs are validated before playback. Station images are downloaded from radio-browser.info and cached locally.

## Security Update Process
Security patches are delivered via normal app update channels (GitHub Releases, automated update notifications). Critical vulnerabilities may trigger an out-of-band security update.

## Contacts
- General issues: [GitHub Issues](https://github.com/michatec/Radio/issues)
- Project maintainer: [@michatec](https://github.com/michatec)