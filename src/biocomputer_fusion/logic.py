"""Protein/gene-circuit inspired logic.

The implementation is boolean software logic. Gate names borrow terminology
from biological logic-circuit modeling, but the code is not a biological build.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable, Dict, Iterable, List


GateFunction = Callable[..., bool]


def _normalize_inputs(values: Iterable[bool]) -> List[bool]:
    normalized = [bool(value) for value in values]
    if not normalized:
        raise ValueError("At least one input is required.")
    return normalized


def gate_and(*values: bool) -> bool:
    values = _normalize_inputs(values)
    return all(values)


def gate_or(*values: bool) -> bool:
    values = _normalize_inputs(values)
    return any(values)


def gate_not(value: bool) -> bool:
    return not bool(value)


def gate_xor(*values: bool) -> bool:
    values = _normalize_inputs(values)
    return sum(values) % 2 == 1


def gate_nand(*values: bool) -> bool:
    return not gate_and(*values)


def gate_nor(*values: bool) -> bool:
    return not gate_or(*values)


@dataclass
class LogicNetwork:
    """Registry of named boolean gates."""

    gates: Dict[str, GateFunction] = field(default_factory=dict)

    def __post_init__(self) -> None:
        self.gates.update(
            {
                "AND": gate_and,
                "OR": gate_or,
                "NOT": gate_not,
                "XOR": gate_xor,
                "NAND": gate_nand,
                "NOR": gate_nor,
            }
        )

    def evaluate(self, gate: str, *inputs: bool) -> bool:
        name = gate.upper()
        try:
            fn = self.gates[name]
        except KeyError as exc:
            raise KeyError(f"Unknown gate {gate!r}. Available: {sorted(self.gates)}") from exc
        return fn(*inputs)

    def truth_table(self, gate: str, arity: int = 2) -> list[dict[str, object]]:
        if arity < 1:
            raise ValueError("arity must be >= 1")
        rows: list[dict[str, object]] = []
        for n in range(2**arity):
            inputs = tuple(bool((n >> bit) & 1) for bit in reversed(range(arity)))
            rows.append({"inputs": inputs, "output": self.evaluate(gate, *inputs)})
        return rows
