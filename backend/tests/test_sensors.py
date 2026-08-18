import numpy as np
import pytest

from app.core.voxel_engine import VoxelGrid
from app.sensors.registry import SENSOR_REGISTRY


def _grid_with_pipe() -> VoxelGrid:
    grid = VoxelGrid(16, 16, 10, voxel_size_m=0.5)
    grid.fill("dry_soil")
    grid.embed_cylinder("metal_pipe", (0, 8, 3), (15, 8, 3), 1.2)
    return grid


@pytest.mark.parametrize("sensor_key", list(SENSOR_REGISTRY.keys()))
def test_simulate_produces_normalized_volume(sensor_key):
    grid = _grid_with_pipe()
    simulator = SENSOR_REGISTRY[sensor_key]
    rng = np.random.default_rng(0)

    result = simulator.simulate(grid, rng, noise_level=0.1)

    assert result.probability_volume.shape == grid.shape
    assert result.probability_volume.min() >= 0.0
    assert result.probability_volume.max() <= 1.0 + 1e-9
    assert result.profile_2d.ndim == 2
    assert result.sensor_key == sensor_key


def test_magnetometer_flags_metal_pipe_more_than_empty_ground():
    grid_with_pipe = _grid_with_pipe()
    grid_empty = VoxelGrid(16, 16, 10, voxel_size_m=0.5)
    grid_empty.fill("dry_soil")

    simulator = SENSOR_REGISTRY["magnetometer"]
    rng = np.random.default_rng(0)

    with_pipe = simulator.simulate(grid_with_pipe, rng, noise_level=0.0)
    empty = simulator.simulate(grid_empty, np.random.default_rng(0), noise_level=0.0)

    assert with_pipe.probability_volume.std() >= empty.probability_volume.std()


def test_gpr_reflectivity_detects_void():
    grid = VoxelGrid(14, 14, 10, voxel_size_m=0.4)
    grid.fill("dry_soil")
    grid.embed_sphere("void", (7, 7, 4), 2.0)

    simulator = SENSOR_REGISTRY["gpr"]
    result = simulator.simulate(grid, np.random.default_rng(1), noise_level=0.0)

    void_region_signal = result.probability_volume[5:10, 5:10, 2:6].mean()
    background_signal = result.probability_volume[:3, :3, :].mean()
    assert void_region_signal > background_signal
