import { useEffect, useMemo, useRef } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import { OrbitControls, Grid } from "@react-three/drei";
import * as THREE from "three";
import type { AnomalyOut, Material, SparseVoxelValue, VoxelOut } from "../types";
import { heatColor } from "../colormap";

export type ViewerMode = "geology" | "value";

interface Props {
  shape: [number, number, number];
  voxelSizeM: number;
  geologyVoxels?: VoxelOut[];
  materials?: Material[];
  sparseVolume?: SparseVoxelValue[];
  mode: ViewerMode;
  anomalies?: AnomalyOut[];
}

function toWorld(x: number, y: number, z: number, shape: [number, number, number], voxelSizeM: number): [number, number, number] {
  const [nx, ny] = shape;
  return [(x - nx / 2) * voxelSizeM, -z * voxelSizeM, (y - ny / 2) * voxelSizeM];
}

interface GeologyVoxelsProps {
  voxels: VoxelOut[];
  materials: Material[];
  shape: [number, number, number];
  voxelSizeM: number;
}

function GeologyVoxels({ voxels, materials, shape, voxelSizeM }: GeologyVoxelsProps) {
  const meshRef = useRef<THREE.InstancedMesh>(null);
  const colorByMaterial = useMemo(() => {
    const map = new Map<number, THREE.Color>();
    materials.forEach((m) => map.set(m.id, new THREE.Color(m.color)));
    return map;
  }, [materials]);

  useEffect(() => {
    const mesh = meshRef.current;
    if (!mesh) return;
    const dummy = new THREE.Object3D();
    voxels.forEach((v, i) => {
      const [wx, wy, wz] = toWorld(v.x, v.y, v.z, shape, voxelSizeM);
      dummy.position.set(wx, wy, wz);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
      const color = colorByMaterial.get(v.m) ?? new THREE.Color("#888888");
      mesh.setColorAt(i, color);
    });
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
  }, [voxels, colorByMaterial, shape, voxelSizeM]);

  return (
    <instancedMesh ref={meshRef} args={[undefined, undefined, Math.max(1, voxels.length)]}>
      <boxGeometry args={[voxelSizeM * 0.94, voxelSizeM * 0.94, voxelSizeM * 0.94]} />
      <meshStandardMaterial />
    </instancedMesh>
  );
}

function ValueVoxels({ voxels, shape, voxelSizeM }: { voxels: SparseVoxelValue[]; shape: [number, number, number]; voxelSizeM: number }) {
  const meshRef = useRef<THREE.InstancedMesh>(null);

  useEffect(() => {
    const mesh = meshRef.current;
    if (!mesh) return;
    const dummy = new THREE.Object3D();
    voxels.forEach((v, i) => {
      const [wx, wy, wz] = toWorld(v.x, v.y, v.z, shape, voxelSizeM);
      dummy.position.set(wx, wy, wz);
      const scale = 0.5 + 0.5 * v.v;
      dummy.scale.setScalar(scale);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
      const [r, g, b] = heatColor(v.v);
      mesh.setColorAt(i, new THREE.Color(r / 255, g / 255, b / 255));
    });
    mesh.instanceMatrix.needsUpdate = true;
    if (mesh.instanceColor) mesh.instanceColor.needsUpdate = true;
  }, [voxels, shape, voxelSizeM]);

  return (
    <instancedMesh ref={meshRef} args={[undefined, undefined, Math.max(1, voxels.length)]}>
      <boxGeometry args={[voxelSizeM * 0.9, voxelSizeM * 0.9, voxelSizeM * 0.9]} />
      <meshStandardMaterial transparent opacity={0.85} />
    </instancedMesh>
  );
}

function AnomalyMarkers({ anomalies, shape, voxelSizeM }: { anomalies: AnomalyOut[]; shape: [number, number, number]; voxelSizeM: number }) {
  return (
    <group>
      {anomalies.map((a) => {
        const [wx, wy, wz] = toWorld(a.centroid_vox[0], a.centroid_vox[1], a.centroid_vox[2], shape, voxelSizeM);
        const sizeX = (a.bbox_vox[0][1] - a.bbox_vox[0][0] + 2) * voxelSizeM;
        const sizeY = (a.bbox_vox[2][1] - a.bbox_vox[2][0] + 2) * voxelSizeM;
        const sizeZ = (a.bbox_vox[1][1] - a.bbox_vox[1][0] + 2) * voxelSizeM;
        return (
          <group key={a.id} position={[wx, wy, wz]}>
            <mesh>
              <boxGeometry args={[sizeX, sizeY, sizeZ]} />
              <meshBasicMaterial color="#ff3b3b" wireframe />
            </mesh>
            <mesh position={[0, sizeY / 2 + 0.25, 0]}>
              <sphereGeometry args={[0.14, 12, 12]} />
              <meshBasicMaterial color="#ff9d6c" />
            </mesh>
          </group>
        );
      })}
    </group>
  );
}

function Rig() {
  useFrame(({ camera }) => {
    camera.updateProjectionMatrix();
  });
  return null;
}

export default function VoxelViewer3D({ shape, voxelSizeM, geologyVoxels, materials, sparseVolume, mode, anomalies }: Props) {
  const [nx, , nz] = shape;
  const span = Math.max(nx, nz) * voxelSizeM;

  return (
    <Canvas camera={{ position: [span * 0.9, span * 0.75, span * 0.9], fov: 45 }} shadows={false}>
      <color attach="background" args={["#0a1018"]} />
      <ambientLight intensity={0.65} />
      <directionalLight position={[10, 20, 10]} intensity={0.9} />
      <Grid
        position={[0, 0.02, 0]}
        args={[span * 1.6, span * 1.6]}
        cellColor="#1b2a3a"
        sectionColor="#274357"
        fadeDistance={span * 3}
      />
      {mode === "geology" && geologyVoxels && materials && (
        <GeologyVoxels voxels={geologyVoxels} materials={materials} shape={shape} voxelSizeM={voxelSizeM} />
      )}
      {mode === "value" && sparseVolume && <ValueVoxels voxels={sparseVolume} shape={shape} voxelSizeM={voxelSizeM} />}
      {anomalies && anomalies.length > 0 && <AnomalyMarkers anomalies={anomalies} shape={shape} voxelSizeM={voxelSizeM} />}
      <OrbitControls makeDefault enableDamping dampingFactor={0.08} />
      <Rig />
    </Canvas>
  );
}
