"""DNA-like data encoding.

This module maps bytes to an abstract four-symbol alphabet. It is a digital
encoding scheme, not a biological design recommendation.
"""

from __future__ import annotations

from dataclasses import dataclass


_BITS_TO_BASE = {
    "00": "A",
    "01": "C",
    "10": "G",
    "11": "T",
}
_BASE_TO_BITS = {v: k for k, v in _BITS_TO_BASE.items()}


@dataclass(frozen=True)
class DNAAlphabetCodec:
    """Encode bytes/text as DNA-like strings and decode them back."""

    alphabet: str = "ACGT"

    def encode_bytes(self, payload: bytes) -> str:
        """Encode bytes into an A/C/G/T string."""
        bits = "".join(f"{byte:08b}" for byte in payload)
        return "".join(_BITS_TO_BASE[bits[i : i + 2]] for i in range(0, len(bits), 2))

    def decode_bytes(self, sequence: str) -> bytes:
        """Decode an A/C/G/T string into bytes."""
        self._validate(sequence)
        bit_string = "".join(_BASE_TO_BITS[base] for base in sequence)
        if len(bit_string) % 8 != 0:
            raise ValueError("DNA-like sequence length does not align to whole bytes.")
        return bytes(int(bit_string[i : i + 8], 2) for i in range(0, len(bit_string), 8))

    def encode_text(self, text: str, encoding: str = "utf-8") -> str:
        """Encode text as a DNA-like string."""
        return self.encode_bytes(text.encode(encoding))

    def decode_text(self, sequence: str, encoding: str = "utf-8") -> str:
        """Decode a DNA-like string into text."""
        return self.decode_bytes(sequence).decode(encoding)

    def _validate(self, sequence: str) -> None:
        invalid = set(sequence) - set(self.alphabet)
        if invalid:
            raise ValueError(f"Invalid DNA-like symbols: {sorted(invalid)}")
