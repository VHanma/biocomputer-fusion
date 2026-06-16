from biocomputer_fusion.codex import DNAAlphabetCodec


def test_text_roundtrip():
    codec = DNAAlphabetCodec()
    seq = codec.encode_text("HELLO")
    assert set(seq) <= set("ACGT")
    assert codec.decode_text(seq) == "HELLO"


def test_invalid_decode():
    codec = DNAAlphabetCodec()
    try:
        codec.decode_text("ACGX")
    except ValueError as exc:
        assert "Invalid DNA-like symbols" in str(exc)
    else:
        raise AssertionError("Expected ValueError")
