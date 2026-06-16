"""Command-line interface."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from .engine import Biocomputer


def _load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def run_command(args: argparse.Namespace) -> int:
    program = _load_json(Path(args.program))
    bio = Biocomputer()
    report = bio.run_program(program)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


def truth_table_command(args: argparse.Namespace) -> int:
    bio = Biocomputer()
    rows = bio.logic.truth_table(args.gate, args.arity)
    print(json.dumps(rows, indent=2))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="biocomputer",
        description="Programmable biocomputer simulator.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    run_parser = sub.add_parser("run", help="Run a JSON biocomputer program.")
    run_parser.add_argument("program", help="Path to a JSON program file.")
    run_parser.set_defaults(func=run_command)

    table_parser = sub.add_parser("truth-table", help="Print a logic gate truth table.")
    table_parser.add_argument("gate", help="Gate name, e.g. AND, OR, XOR.")
    table_parser.add_argument("--arity", type=int, default=2)
    table_parser.set_defaults(func=truth_table_command)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
