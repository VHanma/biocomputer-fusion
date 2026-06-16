"""Top-level programmable biocomputer engine."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any

from .evolution import EvolutionarySearch, xor_target_cases
from .logic import LogicNetwork
from .memory import DNAMemory
from .metabolic import CellState
from .nano import NanoInterface
from .signals import RNASignalBus
from .wave import WaveField


@dataclass
class Biocomputer:
    """Composable simulation engine."""

    memory: DNAMemory = field(default_factory=DNAMemory)
    state: CellState = field(default_factory=CellState)
    signals: RNASignalBus = field(default_factory=RNASignalBus)
    logic: LogicNetwork = field(default_factory=LogicNetwork)
    wave: WaveField = field(default_factory=WaveField)

    def __post_init__(self) -> None:
        self.nano = NanoInterface(memory=self.memory, state=self.state)

    def run_program(self, program: dict[str, Any]) -> dict[str, Any]:
        """Run a JSON-compatible program document."""
        report: dict[str, Any] = {
            "name": program.get("name", "unnamed-program"),
            "writes": [],
            "logic": [],
            "signals": [],
            "wave": None,
            "evolution": None,
            "scan": None,
            "state": None,
        }

        wave_config = program.get("wave") or {}
        if wave_config:
            self.wave = WaveField(
                carrier=wave_config.get("carrier", self.wave.carrier),
                frequency=float(wave_config.get("frequency", self.wave.frequency)),
                amplitude=float(wave_config.get("amplitude", self.wave.amplitude)),
                phase=float(wave_config.get("phase", self.wave.phase)),
                enabled=bool(wave_config.get("enabled", False)),
            )

        for item in program.get("write", []):
            address = item["address"]
            if "text" in item:
                sequence = self.nano.write_text(address, item["text"])
                report["writes"].append(
                    {"address": address, "mode": "text", "sequence": sequence}
                )
            elif "sequence" in item:
                self.nano.write_sequence(address, item["sequence"])
                report["writes"].append(
                    {"address": address, "mode": "sequence", "sequence": item["sequence"]}
                )
            else:
                raise ValueError("write item must contain text or sequence")

        for item in program.get("logic", []):
            gate = item["gate"]
            inputs = tuple(bool(v) for v in item["inputs"])
            output = self.logic.evaluate(gate, *inputs)
            label = item.get("label", gate)
            self.state.set_output(label, output)
            self.state.spend(0.05 * len(inputs), f"logic-{gate}")
            report["logic"].append(
                {"label": label, "gate": gate.upper(), "inputs": inputs, "output": output}
            )

        for item in program.get("signals", []):
            signal = self.signals.emit(
                name=item["name"],
                payload=item.get("payload", ""),
                ttl=int(item.get("ttl", 1)),
            )
            self.state.transition("signal")
            self.state.spend(0.03, f"signal-{signal.name}")
            report["signals"].append(signal.__dict__.copy())

        if program.get("evolution", {}).get("enabled", False):
            evo_cfg = program.get("evolution", {})
            search = EvolutionarySearch(
                population_size=int(evo_cfg.get("population_size", 24)),
                program_length=int(evo_cfg.get("program_length", 3)),
                mutation_rate=float(evo_cfg.get("mutation_rate", 0.15)),
                seed=evo_cfg.get("seed"),
            )
            report["evolution"] = search.search(
                cases=xor_target_cases(),
                generations=int(evo_cfg.get("generations", 20)),
            )
            self.state.spend(1.0, "evolutionary-search")

        first_sequence = None
        memory_snapshot = self.memory.snapshot()
        if memory_snapshot:
            first_sequence = next(iter(memory_snapshot.values()))
        report["wave"] = self.wave.report(first_sequence or "")
        report["scan"] = self.nano.scan()
        self.state.recycle()
        report["state"] = self.state.snapshot()
        return report
