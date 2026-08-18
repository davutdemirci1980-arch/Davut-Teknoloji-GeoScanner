import numpy as np

from app.core.materials import MATERIALS
from app.core.voxel_engine import VoxelGrid


def test_fill_and_property_grid():
    grid = VoxelGrid(4, 4, 4)
    grid.fill("bedrock")
    density = grid.property_grid("density_kg_m3")
    assert np.all(density == MATERIALS["bedrock"].density_kg_m3)


def test_embed_sphere_marks_void():
    grid = VoxelGrid(10, 10, 10)
    grid.fill("dry_soil")
    grid.embed_sphere("void", (5, 5, 5), 2.0)
    assert grid.material_ids[5, 5, 5] == MATERIALS["void"].id
    assert grid.material_ids[0, 0, 0] == MATERIALS["dry_soil"].id


def test_embed_cylinder_runs_along_axis():
    grid = VoxelGrid(10, 10, 5)
    grid.fill("dry_soil")
    grid.embed_cylinder("metal_pipe", (0, 5, 2), (9, 5, 2), 1.0)
    assert grid.material_ids[0, 5, 2] == MATERIALS["metal_pipe"].id
    assert grid.material_ids[9, 5, 2] == MATERIALS["metal_pipe"].id
    assert grid.material_ids[0, 0, 0] == MATERIALS["dry_soil"].id


def test_set_layer_respects_undulation():
    grid = VoxelGrid(6, 6, 8)
    grid.fill("dry_soil")
    undulation = np.zeros((6, 6), dtype=int)
    undulation[0, 0] = 2
    grid.set_layer(0, 2, "bedrock", undulation=undulation)
    assert grid.material_ids[0, 0, 3] == MATERIALS["bedrock"].id
    assert grid.material_ids[5, 5, 3] == MATERIALS["dry_soil"].id


def test_downsample_reduces_shape():
    grid = VoxelGrid(20, 20, 20)
    grid.fill("sand")
    small = grid.downsample(max_dim=10)
    assert max(small.shape) <= 10


def test_add_random_noise_keeps_background_only():
    grid = VoxelGrid(15, 15, 15)
    grid.fill("dry_soil")
    rng = np.random.default_rng(1)
    grid.add_random_noise(rng, flip_probability=0.3)
    from app.core.materials import BACKGROUND_MATERIAL_KEYS

    background_ids = {MATERIALS[k].id for k in BACKGROUND_MATERIAL_KEYS}
    assert set(np.unique(grid.material_ids)).issubset(background_ids)
