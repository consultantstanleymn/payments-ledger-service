# Risk decision: static API keys

| Field | Value |
|---|---|
| Risk | A static API key can leak. Anyone with the key can write to the ledger as that client. |
| Options | Accept. Mitigate with OAuth2 client credentials now. Transfer to an API gateway. |
| Decision | Accept, until OAuth2 client credentials arrive. |
| Owner | Stanley Sujith Nelavala |
| Review date | 2027-02-01 |

## Why accept now

The service has no production traffic. OAuth2 needs an identity provider that does not exist in this project. The cost of the work is larger than the current exposure.

## Compensating controls

- The key maps to a client name. The audit log stores the name, never the key.
- The append-only caller audit log records every call, including rejected ones.
- The latency and availability alerts show unusual traffic.
- Keys come from environment variables. No key is stored in the repo except the local development key.

## Condition to revisit

Review earlier if the service gets a real client, a real network exposure, or a leaked key.
