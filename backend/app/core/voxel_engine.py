"""Full-voxel subsurface model engine.

A VoxelGrid represents a rectangular volume of ground as a 3D array of
material ids. X and Y are horizontal (surface) axes, Z is depth below the
surface (z=0 is the surface, z increases downward). All sensor simulators
and the AI fusion/analysis engine operate directly on this voxel grid, which
is what makes the laboratory "fully voxel based".
"""
from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np

from app.core.materials import MATERIALS, MATERIALS_BY_ID, material_property_array


@dataclass
class VoxelGrid:
    nx: int
    ny: int
    nz: int
    voxel_size_m: float = 0.5
    material_ids: np.ndarray = field(default=None)  # type: ignore[assignment]

    def __post_init__(self) -> None:
        if self.material_ids is None:
            self.material_ids = np.zeros((self.nx, self.ny, self.nz), dtype=np.int32)

    @property
    def shape(self) -> tuple[int, int, int]:
        return (self.nx, self.ny, self.nz)

    @property
    def dimensions_m(self) -> tuple[float, float, float]:
        return (self.nx * self.voxel_size_m, self.ny * self.voxel_size_m, self.nz * self.voxel_size_m)

    # ---- generation helpers -------------------------------------------------

    def fill(self, material_key: str) -> None:
        self.material_ids[:, :, :] = MATERIALS[material_key].id

    def set_layer(self, z_start: int, z_end: int, material_key: str, undulation: np.ndarray | None = None) -> None:
        """Fill a horizontal depth band [z_start, z_end) with a material.

        If `undulation` (an (nx, ny) int array of +/- voxel offsets) is
        given, the layer boundary follows gently varying terrain instead of
        being perfectly flat, which keeps geology-looking scenarios from
        being trivially uniform.
        """
        mid = MATERIALS[material_key].id
        z0 = max(0, z_start)
        z1 = min(self.nz, z_end)
        if undulation is None:
            self.material_ids[:, :, z0:z1] = mid
            return
        for x in range(self.nx):
            for y in range(self.ny):
                offset = int(undulation[x, y])
                lo = max(0, min(self.nz, z0 + offset))
                hi = max(0, min(self.nz, z1 + offset))
                if hi > lo:
                    self.material_ids[x, y, lo:hi] = mid

    def embed_box(self, material_key: str, center_vox: tuple[int, int, int], size_vox: tuple[int, int, int]) -> None:
        mid = MATERIALS[material_key].id
        cx, cy, cz = center_vox
        sx, sy, sz = size_vox
        x0, x1 = max(0, cx - sx // 2), min(self.nx, cx + (sx + 1) // 2)
        y0, y1 = max(0, cy - sy // 2), min(self.ny, cy + (sy + 1) // 2)
        z0, z1 = max(0, cz - sz // 2), min(self.nz, cz + (sz + 1) // 2)
        self.material_ids[x0:x1, y0:y1, z0:z1] = mid

    def embed_sphere(self, material_key: str, center_vox: tuple[float, float, float], radius_vox: float) -> None:
        mid = MATERIALS[material_key].id
        xs, ys, zs = np.ogrid[0:self.nx, 0:self.ny, 0:self.nz]
        cx, cy, cz = center_vox
        dist2 = (xs - cx) ** 2 + (ys - cy) ** 2 + (zs - cz) ** 2
        mask = dist2 <= radius_vox**2
        self.material_ids[mask] = mid

    def embed_cylinder(
        self,
        material_key: str,
        start_vox: tuple[float, float, float],
        end_vox: tuple[float, float, float],
        radius_vox: float,
    ) -> None:
        """Embed a capped cylinder, e.g. for a buried pipe or utility line."""
        mid = MATERIALS[material_key].id
        p0 = np.array(start_vox, dtype=float)
        p1 = np.array(end_vox, dtype=float)
        axis = p1 - p0
        length2 = float(axis @ axis)
        if length2 < 1e-9:
            self.embed_sphere(material_key, start_vox, radius_vox)
            return

        xs, ys, zs = np.meshgrid(
            np.arange(self.nx), np.arange(self.ny), np.arange(self.nz), indexing="ij"
        )
        pts = np.stack([xs, ys, zs], axis=-1).astype(float) - p0
        t = np.clip((pts @ axis) / length2, 0.0, 1.0)
        proj = t[..., None] * axis
        perp_dist2 = np.sum((pts - proj) ** 2, axis=-1)
        mask = perp_dist2 <= radius_vox**2
        self.material_ids[mask] = mid

    def add_random_noise(self, rng: np.random.Generator, flip_probability: float = 0.01) -> None:
        """Small-scale geological speckling (background materials only) to
        avoid perfectly clean layers, without fabricating fake buried objects.
        """
        from app.core.materials import BACKGROUND_MATERIAL_KEYS

        background_ids = np.array([MATERIALS[k].id for k in BACKGROUND_MATERIAL_KEYS])
        flips = rng.random(self.material_ids.shape) < flip_probability
        if not flips.any():
            return
        jitter = background_ids[rng.integers(0, len(background_ids), size=int(flips.sum()))]
        self.material_ids[flips] = jitter

    # ---- property lookups -----------------------------------------------

    def property_grid(self, field_name: str) -> np.ndarray:
        """Vectorized per-voxel physical property lookup (nx, ny, nz)."""
        lut = np.array(material_property_array(field_name), dtype=np.float64)
        return lut[self.material_ids]

    def is_anomaly_grid(self) -> np.ndarray:
        lut = np.array([MATERIALS_BY_ID[i].is_anomaly for i in range(len(MATERIALS_BY_ID))], dtype=bool)
        return lut[self.material_ids]

    # ---- transport / serialization ---------------------------------------

    def downsample(self, max_dim: int = 40) -> "VoxelGrid":
        """Return a coarser copy for transport, capping every axis at max_dim."""
        factor = max(1, int(np.ceil(max(self.nx, self.ny, self.nz) / max_dim)))
        if factor == 1:
            return self
        ds = self.material_ids[::factor, ::factor, ::factor]
        out = VoxelGrid(ds.shape[0], ds.shape[1], ds.shape[2], self.voxel_size_m * factor, ds.copy())
        return out

    def to_sparse_voxel_list(self, skip_material_key: str | None = "dry_soil", stride: int = 1) -> list[dict]:
        """Serialize non-background voxels as a compact list for the frontend."""
        ids = self.material_ids[::stride, ::stride, ::stride]
        voxels: list[dict] = []
        skip_id = MATERIALS[skip_material_key].id if skip_material_key else None
        nz_idx = np.argwhere(ids >= 0)
        for x, y, z in nz_idx:
            mid = int(ids[x, y, z])
            if skip_id is not None and mid == skip_id:
                continue
            voxels.append({"x": int(x), "y": int(y), "z": int(z), "m": mid})
        return voxels
