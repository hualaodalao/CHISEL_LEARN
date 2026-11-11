"""Scheduling-policy registry.

A dispatcher receives an already-costed graph and returns a ScheduleResult.  It
must not change cost estimates; it only chooses how fixed reservations are
placed.
"""

from __future__ import annotations

from typing import Callable

from ..common.opspec import TaskGraph
from ..hw.topology import Topology
from .ledger import EngineCost
from .residency import BufferPlan
from .scheduler import ScheduleResult, schedule

Dispatcher = Callable[[TaskGraph, dict[str, dict[str, EngineCost]], Topology,
                       BufferPlan | None], ScheduleResult]

DISPATCHERS: dict[str, Dispatcher] = {}


def register_dispatcher(name: str, dispatcher: Dispatcher, *,
                        replace: bool = False) -> None:
    if not name:
        raise ValueError("dispatcher requires a name")
    if name in DISPATCHERS and not replace:
        raise ValueError(f"dispatcher already registered: {name}")
    DISPATCHERS[name] = dispatcher


def dispatch(name: str, graph: TaskGraph, costs: dict[str, dict[str, EngineCost]],
             topology: Topology, buffer_plan: BufferPlan | None) -> ScheduleResult:
    try:
        implementation = DISPATCHERS[name]
    except KeyError:
        raise KeyError(f"unregistered dispatcher: {name}") from None
    return implementation(graph, costs, topology, buffer_plan)


def _serial(graph, costs, topology, buffer_plan):
    return schedule(graph, costs, topology, buffer_plan, policy="serial")


def _list(graph, costs, topology, buffer_plan):
    return schedule(graph, costs, topology, buffer_plan, policy="list")


register_dispatcher("serial", _serial)
register_dispatcher("list", _list)
