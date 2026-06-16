"""DNA-like memory store."""

from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
from typing import Dict, Iterable

from .codex import DNAAlphabetCodec


@dataclass
class DNAMemory:
    """Addressable DNA-like storage."""

    codec: DNAAlphabetCodec = field(default_factory=DNAAlphabetCodec)
    _store: Dict[str, str] = field(default_factory=dict)

    def write_sequence(self, address: str, sequence: str) -> None:
        """Write a validated A/C/G/T sequence to an address."""
        self.codec._validate(sequence)
        self._store[address] = sequence

    def read_sequence(self, address: str) -> str:
        """Read an A/C/G/T sequence from an address."""
        try:
            return self._store[address]
        except KeyError as exc:
            raise KeyError(f"No DNA memory at address {address!r}") from exc

    def write_text(self, address: str, text: str) -> str:
        """Encode text and store it."""
        sequence = self.codec.encode_text(text)
        self.write_sequence(address, sequence)
        return sequence

    def read_text(self, address: str) -> str:
        """Read and decode text."""
        return self.codec.decode_text(self.read_sequence(address))

    def delete(self, address: str) -> None:
        """Remove an address."""
        self._store.pop(address, None)

    def addresses(self) -> Iterable[str]:
        """List addresses."""
        return tuple(sorted(self._store))

    def checksum(self, address: str) -> str:
        """Checksum a stored sequence."""
        sequence = self.read_sequence(address).encode("ascii")
        return hashlib.sha256(sequence).hexdigest()

    def snapshot(self) -> dict[str, str]:
        """Return a copy of the memory map."""
        return dict(self._store)
