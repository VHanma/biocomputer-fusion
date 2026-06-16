"""Evolutionary search over tiny simulated boolean programs."""

from __future__ import annotations

from dataclasses import dataclass
import random
from typing import Callable, Iterable, Sequence

from .logic import LogicNetwork


GENES = ("AND", "OR", "XOR", "NAND", "NOR")


@dataclass(frozen=True)
class CandidateProgram:
    """A tiny boolean program represented by a sequence of gate names."""

    gates: tuple[str, ...]

    def run(self, inputs: Sequence[bool], logic: LogicNetwork | None = None) -> bool:
        logic = logic or LogicNetwork()
        values = list(inputs)
        if len(values) < 2:
            raise ValueError("At least two inputs are required.")
        acc = values[0]
        for i, gate in enumerate(self.gates):
            rhs = values[(i + 1) % len(values)]
            acc = logic.evaluate(gate, acc, rhs)
        return acc


@dataclass
class EvolutionarySearch:
    """Simple genetic search for gate sequences matching a target behavior."""

    population_size: int = 24
    program_length: int = 3
    mutation_rate: float = 0.15
    seed: int | None = None

    def __post_init__(self) -> None:
        self.random = random.Random(self.seed)

    def random_program(self) -> CandidateProgram:
        return CandidateProgram(
            gates=tuple(self.random.choice(GENES) for _ in range(self.program_length))
        )

    def mutate(self, program: CandidateProgram) -> CandidateProgram:
        gates = list(program.gates)
        for idx in range(len(gates)):
            if self.random.random() < self.mutation_rate:
                gates[idx] = self.random.choice(GENES)
        return CandidateProgram(tuple(gates))

    def crossover(self, a: CandidateProgram, b: CandidateProgram) -> CandidateProgram:
        if self.program_length == 1:
            return a
        cut = self.random.randrange(1, self.program_length)
        return CandidateProgram(a.gates[:cut] + b.gates[cut:])

    def score(
        self,
        program: CandidateProgram,
        cases: Iterable[tuple[tuple[bool, ...], bool]],
        logic: LogicNetwork | None = None,
    ) -> int:
        logic = logic or LogicNetwork()
        return sum(program.run(inputs, logic) == expected for inputs, expected in cases)

    def search(
        self,
        cases: Iterable[tuple[tuple[bool, ...], bool]],
        generations: int = 20,
    ) -> dict[str, object]:
        cases = tuple(cases)
        population = [self.random_program() for _ in range(self.population_size)]
        best = population[0]
        best_score = -1

        for generation in range(generations):
            ranked = sorted(
                ((self.score(program, cases), program) for program in population),
                key=lambda item: item[0],
                reverse=True,
            )
            if ranked[0][0] > best_score:
                best_score, best = ranked[0]
            if best_score == len(cases):
                break

            survivors = [program for _, program in ranked[: max(2, self.population_size // 4)]]
            next_population = survivors[:]
            while len(next_population) < self.population_size:
                parent_a = self.random.choice(survivors)
                parent_b = self.random.choice(survivors)
                child = self.crossover(parent_a, parent_b)
                next_population.append(self.mutate(child))
            population = next_population

        return {
            "best_gates": best.gates,
            "best_score": best_score,
            "max_score": len(cases),
            "generations_requested": generations,
        }


def xor_target_cases() -> tuple[tuple[tuple[bool, bool], bool], ...]:
    return (
        ((False, False), False),
        ((False, True), True),
        ((True, False), True),
        ((True, True), False),
    )
