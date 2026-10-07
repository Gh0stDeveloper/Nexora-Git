import type { Metadata } from "next";
import { getLatestRelease } from "../../lib/release";

export const revalidate = 60;

export const metadata: Metadata = {
  title: "Verify a build",
  description: "Verify the APK digest and Android signing identity published by Nexora Git.",
  alternates: { canonical: "/verify" },
};

export default async function VerifyPage() {
  const release = await getLatestRelease();

  return (
    <main className="mx-auto max-w-4xl px-4 py-12 sm:px-6 lg:px-8">
      <div className="mb-8 border-b border-[#30363d] pb-6">
        <p className="mb-2 text-sm font-semibold text-[#58a6ff]">Nexora Git</p>
        <h1 className="text-3xl font-bold tracking-tight">Verify a signed build</h1>
        <p className="mt-3 text-sm leading-6 text-[#8b949e]">
          Compare both the APK SHA-256 digest and signing certificate fingerprint before trusting a direct download.
        </p>
      </div>

      {release ? (
        <div className="space-y-8 text-[15px] leading-7 text-[#c9d1d9]">
          <section className="rounded-lg border border-[#30363d] bg-[#0d1117] p-5">
            <h2 className="text-lg font-semibold text-[#f0f6fc]">Published identity</h2>
            <dl className="mt-4 space-y-4">
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-[#8b949e]">Version</dt>
                <dd className="mt-1">v{release.versionName} ({release.versionCode})</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-[#8b949e]">APK SHA-256</dt>
                <dd className="code-value mt-1 break-all rounded-md bg-[#010409] p-3 text-xs">{release.sha256}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-[#8b949e]">Signing certificate SHA-256</dt>
                <dd className="code-value mt-1 break-all rounded-md bg-[#010409] p-3 text-xs">{release.signingCertificateSha256}</dd>
              </div>
              <div>
                <dt className="text-xs font-semibold uppercase tracking-wide text-[#8b949e]">Source commit</dt>
                <dd className="code-value mt-1 break-all">{release.commit}</dd>
              </div>
            </dl>
          </section>

          <section>
            <h2 className="text-xl font-semibold text-[#f0f6fc]">Independent verification</h2>
            <p className="mt-2">On a machine with Android SDK Build Tools installed:</p>
            <pre className="mt-3 overflow-x-auto rounded-lg border border-[#30363d] bg-[#010409] p-4 text-xs leading-6 text-[#c9d1d9]">
              <code>{"sha256sum Nexora-Git.apk\napksigner verify --verbose --print-certs Nexora-Git.apk"}</code>
            </pre>
            <p className="mt-3 text-sm text-[#8b949e]">
              The file digest must match the APK SHA-256 above. The signer certificate SHA-256 reported by apksigner must match the published signing certificate fingerprint.
            </p>
          </section>
        </div>
      ) : (
        <div className="rounded-lg border border-[#30363d] bg-[#0d1117] p-5 text-[#8b949e]">
          No signed APK has been published yet, so there is no production signing identity to verify on this page.
        </div>
      )}
    </main>
  );
}
