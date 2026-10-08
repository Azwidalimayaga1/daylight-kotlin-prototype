# REST API reference

Local origin: `http://127.0.0.1:8080`. Responses are JSON. Except for health, registration and login, requests require `Authorization: Bearer TOKEN`. Authentication responses include a session token and a `user` object. User responses never include a password hash or salt.

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | API/database status |
| POST | `/auth/register` | Create an account and session |
| POST | `/auth/login` | Verify credentials and issue a session |
| POST | `/auth/logout` | Revoke the current session |
| GET | `/me` | Retrieve profile and settings |
| PUT | `/me/settings` | Save all account settings |
| GET | `/habits` | Retrieve this account's habits |
| POST | `/habits` | Add a habit |
| PUT | `/habits/{id}` | Change this account's habit completion |

Register:

```json
{"email":"alex@example.com","password":"SmallSteps2026!","displayName":"Alex"}
```

Login:

```json
{"email":"alex@example.com","password":"SmallSteps2026!"}
```

Settings:

```json
{"displayName":"Alex Morgan","dailyGoal":4,"darkMode":true,"reminders":false}
```

New habit:

```json
{"title":"Take a mindful walk"}
```

Completion:

```json
{"completed":true}
```

Errors use `{"error":"Human-readable message."}` with appropriate HTTP status: 400 invalid input, 401 invalid/expired credentials, 404 endpoint or owned habit missing, 409 duplicate email, 413 excessive body size, 429 throttled attempts. The request-body limit is 16 KiB. Email addresses are normalized to lowercase; passwords remain case-sensitive. Names are 1–60 characters, passwords 8–128, goals 1–10, and habit titles 1–80.

The authentication limiter counts unsuccessful attempts per client IP and blocks at 10 attempts within five minutes. It is in-memory and intended for this local prototype. Successful authentication resets that client's counter.
