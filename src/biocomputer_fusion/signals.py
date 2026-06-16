"""RNA-like transient signal bus."""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List


@dataclass
class RNASignal:
    """A transient symbolic signal."""

    name: str
    payload: str
    ttl: int = 1

    def tick(self) -> bool:
        """Advance time. Return True when the signal remains active."""
        self.ttl -= 1
        return self.ttl > 0


@dataclass
class RNASignalBus:
    """Collection of transient RNA-like signals."""

    _signals: List[RNASignal] = field(default_factory=list)

    def emit(self, name: str, payload: str, ttl: int = 1) -> RNASignal:
        if ttl < 1:
            raise ValueError("ttl must be >= 1")
        signal = RNASignal(name=name, payload=payload, ttl=ttl)
        self._signals.append(signal)
        return signal

    def active(self) -> tuple[RNASignal, ...]:
        return tuple(self._signals)

    def tick(self) -> None:
        self._signals = [signal for signal in self._signals if signal.tick()]

    def find(self, name: str) -> tuple[RNASignal, ...]:
        return tuple(signal for signal in self._signals if signal.name == name)
