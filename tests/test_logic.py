from biocomputer_fusion.logic import LogicNetwork


def test_xor():
    logic = LogicNetwork()
    assert logic.evaluate("XOR", True, False) is True
    assert logic.evaluate("XOR", True, True) is False


def test_truth_table():
    logic = LogicNetwork()
    rows = logic.truth_table("AND", 2)
    assert rows[-1]["output"] is True
    assert sum(row["output"] for row in rows) == 1
