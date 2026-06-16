#!/usr/bin/env bash
set -euo pipefail
python -m pip install -e ".[dev]"
biocomputer run examples/hello_program.json
pytest
