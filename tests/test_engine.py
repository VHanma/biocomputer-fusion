from biocomputer_fusion import Biocomputer


def test_program_runs():
    bio = Biocomputer()
    report = bio.run_program(
        {
            "name": "test",
            "write": [{"address": "a", "text": "hi"}],
            "logic": [{"gate": "AND", "inputs": [True, True], "label": "ok"}],
            "signals": [{"name": "s", "payload": "p", "ttl": 2}],
            "wave": {"enabled": True, "amplitude": 0.1},
        }
    )
    assert report["name"] == "test"
    assert report["logic"][0]["output"] is True
    assert report["wave"]["note"].startswith("Symbolic simulation")
    assert "a" in report["scan"]
