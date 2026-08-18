import type { AnalyzeResponse } from "./types";

function csvEscape(value: string | number): string {
  const s = String(value);
  return /[",\n;]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

export function anomaliesToCsv(result: AnalyzeResponse): string {
  const headers = [
    "id",
    "tahmini_malzeme",
    "malzeme_anahtari",
    "siniflandirma_guveni",
    "fuzyon_sinyal_gucu",
    "sensor_uzlasma_sayisi",
    "voksel_boyutu",
    "hacim_m3",
    "merkez_x_m",
    "merkez_y_m",
    "derinlik_m",
    "dogrulayan_sensorler",
  ];

  const rows = result.anomalies.map((a) =>
    [
      a.id,
      a.predicted_material_name_tr,
      a.predicted_material,
      a.classification_confidence,
      a.fused_confidence,
      a.agreement_count,
      a.size_voxels,
      a.volume_m3,
      a.centroid_m[0],
      a.centroid_m[1],
      a.centroid_m[2],
      a.contributing_sensors.join(";"),
    ]
      .map(csvEscape)
      .join(",")
  );

  return [headers.join(","), ...rows].join("\r\n");
}

// Ambient, minimal shape for the Artifact viewer's optional capability
// bridge. window.claude only exists when the page is running inside an
// Artifact frame with the "downloads" capability declared; everywhere else
// (local dev, the Android app) it is undefined and we fall back to a plain
// browser download.
interface ClaudeDownloadsCapability {
  save(request: { filename: string; data: string }): Promise<{ status: "saved" }>;
}
declare global {
  interface Window {
    claude?: {
      use<T = unknown>(name: string): Promise<T | null>;
    };
  }
}

export async function saveCsvFile(filename: string, csvContent: string): Promise<{ ok: boolean; message: string }> {
  if (typeof window !== "undefined" && window.claude) {
    try {
      const downloads = await window.claude.use<ClaudeDownloadsCapability>("downloads");
      if (downloads) {
        try {
          await downloads.save({ filename, data: csvContent });
          return { ok: true, message: "CSV indirildi." };
        } catch (err) {
          const code = (err as { code?: string })?.code;
          if (code === "extension_not_enabled" || code === "rejected_extension") {
            // .csv isn't allowed in this view (e.g. the mobile app); .txt is
            // always in the base allowlist and has the exact same content.
            const txtFilename = filename.replace(/\.csv$/i, ".txt");
            await downloads.save({ filename: txtFilename, data: csvContent });
            return { ok: true, message: `${txtFilename} olarak indirildi (bu görünümde .csv desteklenmiyor).` };
          }
          throw err;
        }
      }
    } catch (err) {
      const code = (err as { code?: string })?.code;
      if (code === "declined") return { ok: false, message: "İndirme iptal edildi." };
      return { ok: false, message: "CSV indirilemedi, tekrar deneyin." };
    }
  }

  // Plain browser fallback (local dev server, packaged Android app, etc.)
  try {
    const blob = new Blob([csvContent], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    return { ok: true, message: "CSV indirildi." };
  } catch {
    return { ok: false, message: "CSV indirilemedi, tekrar deneyin." };
  }
}
