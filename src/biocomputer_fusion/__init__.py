"""Programmable biocomputer simulator.

The package provides abstract computational models only. It does not implement
wet-lab procedures or claim biological validity for speculative modules.
"""

from .engine import Biocomputer
from .codex import DNAAlphabetCodec
from .logic import LogicNetwork
from .memory import DNAMemory
from .metabolic import CellState
from .nano import NanoInterface
from .signals import RNASignalBus
from .wave import WaveField

__all__ = [
    "Biocomputer",
    "DNAAlphabetCodec",
    "LogicNetwork",
    "DNAMemory",
    "CellState",
    "NanoInterface",
    "RNASignalBus",
    "WaveField",
]
