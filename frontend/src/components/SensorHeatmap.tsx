import { useEffect, useRef } from "react";
import { heatColor } from "../colormap";
import type { SensorResultOut } from "../types";

interface Props {
  result: SensorResultOut;
  title: string;
  height?: number;
}

export default function SensorHeatmap({ result, title, height = 140 }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const grid = result.profile_2d;
    const rows = grid.length;
    const cols = rows > 0 ? grid[0].length : 0;
    if (rows === 0 || cols === 0) return;

    canvas.width = cols;
    canvas.height = rows;

    const image = ctx.createImageData(cols, rows);
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const value = grid[r][c];
        const [red, green, blue] = heatColor(value);
        const idx = (r * cols + c) * 4;
        image.data[idx] = red;
        image.data[idx + 1] = green;
        image.data[idx + 2] = blue;
        image.data[idx + 3] = 255;
      }
    }
    ctx.putImageData(image, 0, 0);
  }, [result]);

  const [axisA, axisB] = result.profile_axes;

  return (
    <div className="heatmap-card">
      <div className="heatmap-header">
        <span className="heatmap-title">{title}</span>
        <span className="heatmap-stats">
          ort {result.stats.mean.toFixed(2)} · maks {result.stats.max.toFixed(2)}
        </span>
      </div>
      <canvas ref={canvasRef} className="heatmap-canvas" style={{ height }} />
      <div className="heatmap-axes">
        <span>{axisA.toUpperCase()} →</span>
        <span>{axisB.toUpperCase()} ↓</span>
      </div>
    </div>
  );
}
