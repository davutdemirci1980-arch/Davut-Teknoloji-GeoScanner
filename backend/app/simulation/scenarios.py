"""Synthetic underground scenario generator.

Builds a full 3D voxel model of a patch of ground: randomized, gently
undulating geological layering plus a configurable set of embedded objects
(utility pipes, cavities, archaeological remains, structures) depending on
the chosen scenario preset. This is the "ground truth" the simulation lab's
sensors then observe indirectly.
"""
from __future__ import annotations

from typing import Literal

import numpy as np

from app.core.voxel_engine import VoxelGrid

ScenarioPreset = Literal["random", "utility_lines", "archaeology", "geology_only"]

_PRESET_ANOMALY_POOLS: dict[str, list[str]] = {
    "random": ["metal_pipe", "pvc_pipe", "void", "concrete", "wood", "archaeological_stone"],
    "utility_lines": ["metal_pipe", "pvc_pipe", "concrete"],
    "archaeology": ["void", "wood", "archaeological_stone", "concrete"],
    "geology_only": [],
}


def _build_layered_geology(grid: VoxelGrid, rng: np.random.Generator) -> None:
    grid.fill("dry_soil")

    sequence = ["dry_soil"]
    if rng.random() < 0.7:
        sequence.append("sand")
    sequence.append("wet_clay")
    has_water_table = rng.random() < 0.35
    if has_water_table:
        sequence.append("groundwater")
    if rng.random() < 0.55:
        sequence.append("limestone")
    sequence.append("bedrock")

    weights = rng.uniform(0.6, 1.4, size=len(sequence))
    weights /= weights.sum()
    thicknesses = np.maximum(1, np.round(weights * grid.nz)).astype(int)
    while thicknesses.sum() > grid.nz:
        thicknesses[np.argmax(thicknesses)] -= 1
    while thicknesses.sum() < grid.nz:
        thicknesses[np.argmin(thicknesses)] += 1

    xs, ys = np.meshgrid(np.arange(grid.nx), np.arange(grid.ny), indexing="ij")
    amp = max(1.0, grid.nz * 0.06)
    undulation = (
        amp * np.sin(xs / max(grid.nx, 1) * 2 * np.pi * rng.uniform(0.6, 1.4) + rng.uniform(0, 2 * np.pi))
        + amp * np.cos(ys / max(grid.ny, 1) * 2 * np.pi * rng.uniform(0.6, 1.4) + rng.uniform(0, 2 * np.pi))
    ).astype(int)

    z = 0
    for material, thickness in zip(sequence, thicknesses):
        grid.set_layer(z, z + int(thickness), material, undulation=undulation)
        z += int(thickness)


def _random_anomaly_shape(
    grid: VoxelGrid, material_key: str, rng: np.random.Generator, max_depth_fraction: float
) -> None:
    margin = max(1, min(grid.nx, grid.ny) // 6)
    max_z = max(2, int(grid.nz * max_depth_fraction))

    if material_key in ("metal_pipe", "pvc_pipe"):
        depth = int(rng.integers(1, max(2, int(grid.nz * 0.35))))
        radius = rng.uniform(0.6, 1.4)
        axis_choice = rng.random()
        if axis_choice < 0.5:
            y = rng.integers(margin, max(margin + 1, grid.ny - margin))
            grid.embed_cylinder(material_key, (0, y, depth), (grid.nx - 1, y, depth), radius)
        else:
            x = rng.integers(margin, max(margin + 1, grid.nx - margin))
            grid.embed_cylinder(material_key, (x, 0, depth), (x, grid.ny - 1, depth), radius)
        return

    cx = rng.integers(margin, max(margin + 1, grid.nx - margin))
    cy = rng.integers(margin, max(margin + 1, grid.ny - margin))
    cz = rng.integers(max(1, max_z // 3), max_z)

    if material_key == "void":
        radius = rng.uniform(1.0, 2.2)
        grid.embed_sphere(material_key, (cx, cy, cz), radius)
    elif material_key in ("concrete", "archaeological_stone"):
        size = (int(rng.integers(2, 5)), int(rng.integers(2, 5)), int(rng.integers(2, 4)))
        grid.embed_box(material_key, (cx, cy, cz), size)
    else:  # wood
        size = (int(rng.integers(2, 4)), int(rng.integers(1, 3)), int(rng.integers(1, 2)) + 1)
        grid.embed_box(material_key, (cx, cy, cz), size)


def generate_scenario(
    nx: int = 28,
    ny: int = 28,
    nz: int = 18,
    voxel_size_m: float = 0.5,
    preset: ScenarioPreset = "random",
    num_anomalies: int = 5,
    seed: int | None = None,
    speckle_noise: float = 0.01,
) -> VoxelGrid:
    rng = np.random.default_rng(seed)
    grid = VoxelGrid(nx, ny, nz, voxel_size_m)

    _build_layered_geology(grid, rng)

    pool = _PRESET_ANOMALY_POOLS.get(preset, _PRESET_ANOMALY_POOLS["random"])
    if pool and num_anomalies > 0:
        for _ in range(num_anomalies):
            material_key = pool[rng.integers(0, len(pool))]
            _random_anomaly_shape(grid, material_key, rng, max_depth_fraction=0.75)

    if speckle_noise > 0:
        grid.add_random_noise(rng, flip_probability=speckle_noise)

    return grid
