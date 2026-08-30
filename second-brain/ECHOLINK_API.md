# EchoLink Local Bridge

EchoLink is a localhost-only bridge for EchoCore Omega v5.

- Default endpoint: `http://127.0.0.1:18432`
- Authentication: `X-EchoCore-Token: <token>` or JSON/query `token`
- Bridge UI: open **EchoCore Omega → ECHOLINK**
- Permissions are separate for read, write, sources, and projects.

## Endpoints

- `GET /ping`
- `GET /capabilities`
- `GET /brain/search?q=topic&limit=8`
- `POST /brain/answer`
- `POST /memory/add`
- `POST /source/import_text`
- `GET /source/search?q=term&limit=8`
- `GET /projects`
- `POST /project/add`
- `POST /task/add`
- `POST /consolidate`

Example:

```bash
curl -H "X-EchoCore-Token: YOUR_TOKEN" \
  "http://127.0.0.1:18432/brain/search?q=focus"
```

Example memory write:

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -H "X-EchoCore-Token: YOUR_TOKEN" \
  -d '{"text":"Remember this","type":"THOUGHT","importance":8}' \
  http://127.0.0.1:18432/memory/add
```
