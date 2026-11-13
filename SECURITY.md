# Security scope

Himaya is a user-controlled DNS filter, not a tamper-proof parental control, full VPN, device management product, or traffic anonymizer. It does not inspect HTTPS content or prevent uninstalling/revoking VPN permission.

## Changes in this preview

- Replace unauthenticated plaintext UDP upstream DNS with TLS and platform certificate/hostname verification. No custom trust manager, trust-all configuration, or plaintext fallback.
- Match transaction ID, question name/type/class and QR/opcode; bound parsing, packet size, record lengths and counts. Reject malformed packets.
- Limit concurrency to four workers and 64 queued packets. At saturation, excess requests are dropped (clients may retry); no unbounded queue.
- Refuse to use sockets if VPN protection fails. Close active sockets, tunnel, work queue and overlay on stop/revoke.
- Use a foreground service and immutable notification intents; retain BIND_VPN_SERVICE protection.
- Remove mandatory overlay permission; provide immediate dismissal for reminders.

- Disable Android debugging in the distributed preview.
- Reject oversized or malformed DNS queries before sending them upstream. Strip EDNS client options, including Client Subnet and cookies, while preserving the advertised UDP size and DO flag.

## Remaining limits

DNS bypass paths and false positives are documented in README. DNS-over-TLS uses a fresh connection per query; connection pooling, network transition behaviour, battery use and IPv6-only networks require further work/testing. DNS TCP transport to the local VPN, DNSSEC authenticity validation by the app, and IP reassembly are not implemented. Upstream availability or category accuracy is not guaranteed.

The application does not store queried domains. Avoid including browsing history, credentials, signing keys or personal information in bug reports. For a vulnerability report, first check whether the repository provides a private reporting channel; do not publicly post a working exploit or sensitive data.

Before release: run all tests/lint, verify TLS failures and reconnect behaviour on real devices, verify signed APK identity, and retain the signing key securely for future updates.
