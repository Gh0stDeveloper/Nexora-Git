import { ImageResponse } from "next/og";

export const alt = "Nexora Git — Git & GitHub workspace for Android";
export const size = { width: 1200, height: 630 };
export const contentType = "image/png";

export default function OpenGraphImage() {
  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          justifyContent: "space-between",
          background: "#0d1117",
          color: "#f0f6fc",
          padding: "72px 82px",
          fontFamily: "Arial, sans-serif",
          border: "1px solid #30363d",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: 28 }}>
          <svg width={108} height={108} viewBox="0 0 108 108" style={{ borderRadius: 22 }}>
            <path fill="#6F42C1" d="M0 0h108v108H0z" />
            <path fill="#FFFFFF" d="M30 24a7 7 0 1 0 0 14 7 7 0 0 0 0-14zm48 46a7 7 0 1 0 0 14 7 7 0 0 0 0-14zm-48 0a7 7 0 1 0 0 14 7 7 0 0 0 0-14z" />
            <path fill="none" stroke="#FFFFFF" strokeWidth={6} strokeLinecap="round" strokeLinejoin="round" d="M30 38v32M37 31h14c15 0 27 12 27 27v12" />
          </svg>
          <div style={{ display: "flex", flexDirection: "column" }}>
            <div style={{ fontSize: 30, color: "#8b949e" }}>Gh0stDeveloper /</div>
            <div style={{ fontSize: 58, fontWeight: 700, letterSpacing: -2 }}>Nexora Git</div>
          </div>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 18, maxWidth: 960 }}>
          <div style={{ fontSize: 42, fontWeight: 650, lineHeight: 1.15 }}>
            A complete Git & GitHub workspace built for Android developers.
          </div>
          <div style={{ fontSize: 25, color: "#8b949e", lineHeight: 1.45 }}>
            Native Git engine · Kotlin + Compose · Open source · Self-hosted signed APK releases
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 14, fontSize: 22, color: "#3fb950" }}>
          <div style={{ width: 12, height: 12, borderRadius: 999, background: "#3fb950" }} />
          Download the latest signed Android release
        </div>
      </div>
    ),
    size,
  );
}
