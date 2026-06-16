# GitHub Setup

Create a new empty repository on GitHub, then run:

```bash
cd biocomputer-fusion
git init
git add .
git commit -m "Initial programmable biocomputer simulator"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/biocomputer-fusion.git
git push -u origin main
```

Local development:

```bash
python -m pip install -e ".[dev]"
biocomputer run examples/hello_program.json
pytest
```

The project is simulation-only. The wave-field module is included as a symbolic computational layer and does not make biological claims.
