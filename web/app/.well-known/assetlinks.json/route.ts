const packageName = "com.nexora.git";

function normalizedFingerprint(): string | null {
  const raw = process.env.NEXORA_ANDROID_APP_LINK_SHA256_CERT_FINGERPRINT?.trim();
  if (!raw) return null;

  const value = raw.toUpperCase();
  return /^([0-9A-F]{2}:){31}[0-9A-F]{2}$/.test(value) ? value : null;
}

export const dynamic = "force-dynamic";

export function GET() {
  const fingerprint = normalizedFingerprint();

  if (!fingerprint) {
    return Response.json(
      { error: "app_link_signing_identity_unavailable" },
      {
        status: 503,
        headers: {
          "Cache-Control": "no-store",
        },
      },
    );
  }

  return Response.json(
    [
      {
        relation: ["delegate_permission/common.handle_all_urls"],
        target: {
          namespace: "android_app",
          package_name: packageName,
          sha256_cert_fingerprints: [fingerprint],
        },
      },
    ],
    {
      headers: {
        "Cache-Control": "public, max-age=3600",
      },
    },
  );
}
