# Biocomputer Fusion

A GitHub-ready **programmable biocomputer simulator** that fuses mainstream biomolecular computing abstractions with an optional wave-field-inspired simulation layer.

This project **does not claim** that wave-field effects reprogram DNA or that speculative wave-genetic theories are biologically established. The wave module is included as a computational metaphor and hypothesis-playground only.

## What it simulates

- **DNA memory**: encode, store, retrieve, and checksum digital data as DNA-like sequences.
- **RNA signals**: transient messages with time-to-live behavior.
- **Protein logic gates**: boolean gates and simple gene-circuit style computation.
- **Metabolic state machine**: tracks energy use, recyclable byproducts, and cell state.
- **Nano-interface**: safe read/write/scan abstraction for interacting with simulated memory.
- **Evolutionary search**: evolves small simulated programs toward a target behavior.
- **Wave-field layer**: optional symbolic resonance/modulation model, included without biological claims.

## Install

```bash
git clone https://github.com/YOUR_USERNAME/biocomputer-fusion.git
cd biocomputer-fusion
python -m pip install -e ".[dev]"
```

## Run the demo

```bash
biocomputer run examples/hello_program.json
```

Expected output includes DNA-like encoded memory, logic gate outputs, cell-state energy accounting, and an optional wave-field report.

## Run tests

```bash
pytest
```

## Minimal Python example

```python
from biocomputer_fusion import Biocomputer

bio = Biocomputer()
bio.nano.write_text("slot:message", "HELLO")
print(bio.nano.read_text("slot:message"))

result = bio.logic.evaluate("XOR", True, False)
print(result)
```

## Project stance

This project is a **simulation toolkit**. It is designed for education, speculative computing experiments, software prototyping, and conceptual modeling.

It avoids wet-lab protocols, genetic engineering instructions, biological transformation procedures, or claims of validated biological effects beyond abstract computational modeling.

## Architecture

```text
User Program JSON
      |
      v
+----------------------+
| Biocomputer Engine   |
+----------+-----------+
           |
           +--> DNA Memory
           +--> RNA Signal Bus
           +--> Logic Gates
           +--> Metabolic State
           +--> Nano Interface
           +--> Evolutionary Search
           +--> Wave Field Simulator
```

## Example program

```json
{
  "name": "hello-fusion",
  "write": [
    {"address": "slot:message", "text": "HELLO BIOCOMPUTER"}
  ],
  "logic": [
    {"gate": "AND", "inputs": [true, true], "label": "viability"},
    {"gate": "XOR", "inputs": [true, false], "label": "toggle"}
  ],
  "signals": [
    {"name": "pulse", "payload": "sync", "ttl": 3}
  ],
  "wave": {
    "enabled": true,
    "carrier": "symbolic-resonance",
    "frequency": 7.83,
    "amplitude": 0.2
  }
}
```


## Repository layout

```text
biocomputer-fusion/
├── examples/
│   ├── hello_program.json
│   ├── storage_only.json
│   └── wave_field_demo.json
├── src/biocomputer_fusion/
│   ├── codex.py
│   ├── memory.py
│   ├── signals.py
│   ├── logic.py
│   ├── metabolic.py
│   ├── nano.py
│   ├── wave.py
│   ├── evolution.py
│   ├── engine.py
│   └── cli.py
├── tests/
├── docs-design.md
├── GITHUB_SETUP.md
└── pyproject.toml
```

## License

MIT
