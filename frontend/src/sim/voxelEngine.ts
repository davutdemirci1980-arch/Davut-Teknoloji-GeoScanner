// TypeScript port of backend/app/core/voxel_engine.py — runs entirely in-browser.
import { MATERIALS, MATERIALS_BY_ID, materialPropertyArray, type MaterialDef } from "./materials";
import type { Rng } from "./rng";

export class VoxelGrid {
  nx: number;
  ny: number;
  nz: number;
  voxelSizeM: number;
  materialIds: Int32Array;

  constructor(nx: number, ny: number, nz: number, voxelSizeM = 0.5, materialIds?: Int32Array) {
    this.nx = nx;
    this.ny = ny;
    this.nz = nz;
    this.voxelSizeM = voxelSizeM;
    this.materialIds = materialIds ?? new Int32Array(nx * ny * nz);
  }

  idx(x: number, y: number, z: number): number {
    return (x * this.ny + y) * this.nz + z;
  }

  get shape(): [number, number, number] {
    return [this.nx, this.ny, this.nz];
  }

  get dimensionsM(): [number, number, number] {
    return [this.nx * this.voxelSizeM, this.ny * this.voxelSizeM, this.nz * this.voxelSizeM];
  }

  fill(materialKey: string): void {
    this.materialIds.fill(MATERIALS[materialKey].id);
  }

  setLayer(zStart: number, zEnd: number, materialKey: string, undulation?: Int32Array): void {
    const mid = MATERIALS[materialKey].id;
    const z0 = Math.max(0, zStart);
    const z1 = Math.min(this.nz, zEnd);
    if (!undulation) {
      for (let x = 0; x < this.nx; x++) {
        for (let y = 0; y < this.ny; y++) {
          for (let z = z0; z < z1; z++) this.materialIds[this.idx(x, y, z)] = mid;
        }
      }
      return;
    }
    for (let x = 0; x < this.nx; x++) {
      for (let y = 0; y < this.ny; y++) {
        const offset = undulation[x * this.ny + y];
        const lo = Math.max(0, Math.min(this.nz, z0 + offset));
        const hi = Math.max(0, Math.min(this.nz, z1 + offset));
        for (let z = lo; z < hi; z++) this.materialIds[this.idx(x, y, z)] = mid;
      }
    }
  }

  embedBox(materialKey: string, center: [number, number, number], size: [number, number, number]): void {
    const mid = MATERIALS[materialKey].id;
    const [cx, cy, cz] = center;
    const [sx, sy, sz] = size;
    const x0 = Math.max(0, Math.floor(cx - sx / 2));
    const x1 = Math.min(this.nx, Math.floor(cx + (sx + 1) / 2));
    const y0 = Math.max(0, Math.floor(cy - sy / 2));
    const y1 = Math.min(this.ny, Math.floor(cy + (sy + 1) / 2));
    const z0 = Math.max(0, Math.floor(cz - sz / 2));
    const z1 = Math.min(this.nz, Math.floor(cz + (sz + 1) / 2));
    for (let x = x0; x < x1; x++) {
      for (let y = y0; y < y1; y++) {
        for (let z = z0; z < z1; z++) this.materialIds[this.idx(x, y, z)] = mid;
      }
    }
  }

  embedSphere(materialKey: string, center: [number, number, number], radiusVox: number): void {
    const mid = MATERIALS[materialKey].id;
    const [cx, cy, cz] = center;
    const r2 = radiusVox * radiusVox;
    for (let x = 0; x < this.nx; x++) {
      for (let y = 0; y < this.ny; y++) {
        for (let z = 0; z < this.nz; z++) {
          const d2 = (x - cx) ** 2 + (y - cy) ** 2 + (z - cz) ** 2;
          if (d2 <= r2) this.materialIds[this.idx(x, y, z)] = mid;
        }
      }
    }
  }

  embedCylinder(
    materialKey: string,
    start: [number, number, number],
    end: [number, number, number],
    radiusVox: number
  ): void {
    const mid = MATERIALS[materialKey].id;
    const [px0, py0, pz0] = start;
    const axis = [end[0] - px0, end[1] - py0, end[2] - pz0];
    const length2 = axis[0] ** 2 + axis[1] ** 2 + axis[2] ** 2;
    if (length2 < 1e-9) {
      this.embedSphere(materialKey, start, radiusVox);
      return;
    }
    const r2 = radiusVox * radiusVox;
    for (let x = 0; x < this.nx; x++) {
      for (let y = 0; y < this.ny; y++) {
        for (let z = 0; z < this.nz; z++) {
          const px = x - px0;
          const py = y - py0;
          const pz = z - pz0;
          let t = (px * axis[0] + py * axis[1] + pz * axis[2]) / length2;
          t = Math.max(0, Math.min(1, t));
          const projX = t * axis[0];
          const projY = t * axis[1];
          const projZ = t * axis[2];
          const perp2 = (px - projX) ** 2 + (py - projY) ** 2 + (pz - projZ) ** 2;
          if (perp2 <= r2) this.materialIds[this.idx(x, y, z)] = mid;
        }
      }
    }
  }

  addRandomNoise(rng: Rng, flipProbability = 0.01): void {
    const backgroundIds = ["dry_soil", "wet_clay", "sand", "bedrock", "limestone", "groundwater"].map(
      (k) => MATERIALS[k].id
    );
    for (let i = 0; i < this.materialIds.length; i++) {
      if (rng.random() < flipProbability) {
        this.materialIds[i] = backgroundIds[rng.integers(0, backgroundIds.length)];
      }
    }
  }

  propertyGrid(field: keyof MaterialDef): Float64Array {
    const lut = materialPropertyArray(field);
    const out = new Float64Array(this.materialIds.length);
    for (let i = 0; i < this.materialIds.length; i++) out[i] = lut[this.materialIds[i]];
    return out;
  }

  isAnomalyGrid(): Uint8Array {
    const lut = new Uint8Array(MATERIALS_BY_ID.length);
    MATERIALS_BY_ID.forEach((m, i) => (lut[i] = m.is_anomaly ? 1 : 0));
    const out = new Uint8Array(this.materialIds.length);
    for (let i = 0; i < this.materialIds.length; i++) out[i] = lut[this.materialIds[i]];
    return out;
  }

  toSparseVoxelList(skipMaterialKey: string | null = "dry_soil"): { x: number; y: number; z: number; m: number }[] {
    const skipId = skipMaterialKey ? MATERIALS[skipMaterialKey].id : null;
    const voxels: { x: number; y: number; z: number; m: number }[] = [];
    for (let x = 0; x < this.nx; x++) {
      for (let y = 0; y < this.ny; y++) {
        for (let z = 0; z < this.nz; z++) {
          const m = this.materialIds[this.idx(x, y, z)];
          if (skipId !== null && m === skipId) continue;
          voxels.push({ x, y, z, m });
        }
      }
    }
    return voxels;
  }
}
