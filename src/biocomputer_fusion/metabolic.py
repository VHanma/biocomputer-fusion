"""Metabolic state accounting for the simulated cell."""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class CellState:
    """Track energy, recyclable byproducts, and symbolic outputs."""

    energy: float = 100.0
    recyclable_byproducts: float = 0.0
    outputs: dict[str, object] = field(default_factory=dict)
    mode: str = "homeostasis"

    def spend(self, amount: float, label: str = "operation") -> None:
        """Spend simulated energy and place byproducts into a recyclable pool."""
        if amount < 0:
            raise ValueError("amount must be non-negative")
        if self.energy < amount:
            raise RuntimeError(f"Insufficient energy for {label}: need {amount}, have {self.energy}")
        self.energy -= amount
        self.recyclable_byproducts += amount * 0.05

    def recycle(self) -> None:
        """Recover a fraction of recyclable byproducts as simulated energy."""
        recovered = self.recyclable_byproducts * 0.8
        self.energy += recovered
        self.recyclable_byproducts = 0.0

    def set_output(self, key: str, value: object) -> None:
        self.outputs[key] = value

    def transition(self, mode: str) -> None:
        """Set a symbolic cellular mode."""
        allowed = {"homeostasis", "compute", "store", "repair", "quiescent", "signal"}
        if mode not in allowed:
            raise ValueError(f"Unknown mode {mode!r}. Allowed: {sorted(allowed)}")
        self.mode = mode

    def snapshot(self) -> dict[str, object]:
        return {
            "energy": round(self.energy, 6),
            "recyclable_byproducts": round(self.recyclable_byproducts, 6),
            "mode": self.mode,
            "outputs": dict(self.outputs),
        }
