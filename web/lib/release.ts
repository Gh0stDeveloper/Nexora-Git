import { readFile } from "node:fs/promises";

export type ReleaseInfo = {
  versionName: string;
  versionCode: number;
  jobId: string;
  commit: string;
  signedAt: string;
  sha256: string;
  sizeBytes: number;
  downloadUrl: string;
};

const metadataPath =
  process.env.NEXORA_WEB_RELEASE_METADATA ??
  "/var/lib/nexora-git/web/releases/current/latest.json";

function isReleaseInfo(value: unknown): value is ReleaseInfo {
  if (!value || typeof value !== "object") return false;
  const item = value as Partial<ReleaseInfo>;
  return (
    typeof item.versionName === "string" &&
    Number.isInteger(item.versionCode) &&
    typeof item.jobId === "string" &&
    /^[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$/.test(item.jobId) &&
    typeof item.commit === "string" &&
    /^[a-f0-9]{40}$/.test(item.commit) &&
    typeof item.signedAt === "string" &&
    typeof item.sha256 === "string" &&
    /^[a-f0-9]{64}$/.test(item.sha256) &&
    typeof item.sizeBytes === "number" &&
    item.sizeBytes > 0 &&
    item.downloadUrl === "/download/nexora-git.apk"
  );
}

export async function getLatestRelease(): Promise<ReleaseInfo | null> {
  try {
    const raw = await readFile(metadataPath, "utf8");
    const parsed: unknown = JSON.parse(raw);
    return isReleaseInfo(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

export function formatBytes(bytes: number): string {
  const units = ["B", "KB", "MB", "GB"];
  let size = bytes;
  let index = 0;

  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }

  return `${size.toFixed(index === 0 ? 0 : 1)} ${units[index]}`;
}

export function formatSignedAt(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("en", {
    year: "numeric",
    month: "short",
    day: "2-digit",
    timeZone: "UTC",
  }).format(date);
}
