# Contributing

Contributions are welcome when they preserve the project boundary:

- Keep modules abstract and simulation-only.
- Do not add wet-lab protocols or genetic engineering procedures.
- Do not present speculative wave-field behavior as validated biology.
- Prefer deterministic tests for simulation features.

Run before opening a pull request:

```bash
ruff check .
pytest
```
