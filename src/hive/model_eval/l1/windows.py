"""窗口计数不包含未知的排空、DMA stall 与命令级气泡。"""

from __future__ import annotations

from collections.abc import Iterable, Iterator
from dataclasses import dataclass

from .gemm_geom import Command, GemmShape


@dataclass(frozen=True)
class Window:
    command_index: int
    n_tile: int
    k_pass: int
    load_beats: int
    consume_beats: int
    c_rows: int


@dataclass(frozen=True)
class WindowCounts:
    commands: int
    windows: int
    a_beats: int
    b_beats: int
    c_rows: int
    c_beats: int
    array_cycles: int
    useful_macs: int
    padded_macs: int
    compute_cycles: int
    load_cycles: int

    @property
    def utilization(self) -> float:
        return self.compute_cycles / self.array_cycles if self.array_cycles else 0.0


def iter_windows(commands: Iterable[Command]) -> Iterator[Window]:
    """独立 K 子命令也输出 C，仅命令内部的中间 K pass 回灌。"""
    for index, command in enumerate(commands):
        for n_tile in range(command.n_tiles):
            for k_pass in range(command.k_tiles):
                yield Window(
                    command_index=index, n_tile=n_tile, k_pass=k_pass,
                    load_beats=command.tile_n, consume_beats=command.m,
                    c_rows=command.m if k_pass == command.k_tiles - 1 else 0,
                )


def count_windows(shape: GemmShape, commands: Iterable[Command]) -> WindowCounts:
    """小格式 C 行不能跨行合拍，每行至少占用一个物理 DMA 拍。"""
    command_count = window_count = a_beats = b_beats = c_rows = c_beats = 0
    issued_macs = 0
    for command in commands:
        command_count += 1
        n_windows = command.n_tiles * command.k_tiles
        window_count += n_windows
        a_beats += n_windows * command.m
        b_beats += n_windows * command.tile_n
        output_rows = command.n_tiles * command.m
        c_rows += output_rows
        bits_per_beat = 8 * command.c_bytes_per_beat
        beats_per_row = max(1, (command.tile_n * command.c_bits + bits_per_beat - 1)
                            // bits_per_beat)
        c_beats += output_rows * beats_per_row
        issued_macs += command.m * command.n * command.k

    useful_macs = shape.m * shape.n * shape.k
    return WindowCounts(
        commands=command_count, windows=window_count,
        a_beats=a_beats, b_beats=b_beats, c_rows=c_rows, c_beats=c_beats,
        array_cycles=b_beats + a_beats, useful_macs=useful_macs,
        padded_macs=issued_macs - useful_macs,
        compute_cycles=a_beats, load_cycles=b_beats,
    )
