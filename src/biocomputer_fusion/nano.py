"""Nano-interface abstraction for simulated read/write operations."""

from __future__ import annotations

from dataclasses import dataclass

from .memory import DNAMemory
from .metabolic import CellState


@dataclass
class NanoInterface:
    """Safe interface to the simulated DNA memory."""

    memory: DNAMemory
    state: CellState
    read_cost: float = 0.1
    write_cost: float = 0.5
    scan_cost: float = 0.2

    def write_text(self, address: str, text: str) -> str:
        self.state.transition("store")
        self.state.spend(self.write_cost + len(text) * 0.01, "nano-write-text")
        return self.memory.write_text(address, text)

    def read_text(self, address: str) -> str:
        self.state.transition("compute")
        self.state.spend(self.read_cost, "nano-read-text")
        return self.memory.read_text(address)

    def write_sequence(self, address: str, sequence: str) -> None:
        self.state.transition("store")
        self.state.spend(self.write_cost + len(sequence) * 0.002, "nano-write-sequence")
        self.memory.write_sequence(address, sequence)

    def read_sequence(self, address: str) -> str:
        self.state.transition("compute")
        self.state.spend(self.read_cost, "nano-read-sequence")
        return self.memory.read_sequence(address)

    def scan(self) -> dict[str, dict[str, object]]:
        self.state.spend(self.scan_cost, "nano-scan")
        return {
            address: {
                "length": len(sequence),
                "checksum": self.memory.checksum(address),
            }
            for address, sequence in self.memory.snapshot().items()
        }
