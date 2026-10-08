import { CheckIcon, CodeIcon, DownloadIcon, ExternalIcon, GitBranchIcon, ShieldIcon, StarIcon } from "../components/icons";
import { formatBytes, formatSignedAt, getLatestRelease } from "../lib/release";

export const dynamic = "force-dynamic";

const source = "https://github.com/Gh0stDeveloper/Nexora-Git";

const features = [
  ["Real local Git", "Clone, branches, commits, merge, rebase, stash, tags, LFS and more through a native libgit2 engine."],
  ["GitHub platform", "Repositories, issues, pull requests, reviews, Actions, releases, profile and advanced GitHub workflows."],
  ["Code on Android", "Project browsing, editing, search, syntax intelligence and repository workflows designed for a phone."],
  ["Security by design", "OAuth + PKCE, encrypted tokens, self-hosted Auth Broker and a persistent verified Android signing identity."],
];

export default async function HomePage() {
  const release = await getLatestRelease();

  return (
    <main>
      <section className="relative overflow-hidden border-b border-[#21262d] bg-[#010409]">
        <div className="github-grid pointer-events-none absolute inset-0 opacity-60" />
        <div className="relative mx-auto max-w-7xl px-4 py-14 sm:px-6 sm:py-20 lg:px-8 lg:py-24">
          <div className="mb-7 flex flex-wrap items-center gap-2 text-sm">
            <a
              href={source}
              className="focus-ring inline-flex items-center gap-2 rounded-md border border-[#30363d] bg-[#0d1117] px-3 py-1.5 text-[#c9d1d9] hover:border-[#8b949e]"
            >
              <GitBranchIcon />
              <span className="text-[#8b949e]">Gh0stDeveloper /</span>
              <strong>Nexora-Git</strong>
            </a>
            <span className="rounded-full border border-[#30363d] px-2 py-0.5 text-xs font-medium text-[#8b949e]">Public</span>
          </div>

          <div className="grid items-start gap-10 lg:grid-cols-[minmax(0,1fr)_390px] lg:gap-14">
            <div>
              <div className="mb-6 flex items-center gap-4">
                <img
                  src="/nexora-git-icon.svg"
                  alt="Nexora Git"
                  className="h-20 w-20 rounded-[18px] shadow-2xl shadow-[#6f42c1]/20 sm:h-24 sm:w-24"
                />
                <div className="min-w-0">
                  <p className="mb-1 text-sm font-medium text-[#8b949e]">Open-source Android development workspace</p>
                  <h1 className="text-4xl font-bold tracking-[-0.04em] text-white sm:text-5xl lg:text-6xl">Nexora Git</h1>
                </div>
              </div>

              <p className="max-w-3xl text-lg leading-8 text-[#b1bac4] sm:text-xl">
                A serious Git and GitHub workspace built natively for Android. Work with complete repositories,
                edit code, manage collaboration and run a real local Git engine without depending on Termux.
              </p>

              <div className="mt-7 flex flex-wrap gap-2 text-xs font-medium text-[#c9d1d9]">
                {["Android 8.0+", "Kotlin", "Jetpack Compose", "libgit2", "Apache-2.0"].map((item) => (
                  <span key={item} className="rounded-full border border-[#30363d] bg-[#161b22] px-3 py-1.5">{item}</span>
                ))}
              </div>

              <div className="mt-8 flex flex-wrap gap-3">
                {release ? (
                  <a
                    href={release.downloadUrl}
                    className="focus-ring inline-flex items-center gap-2 rounded-md border border-[#2ea043] bg-[#238636] px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-[#2ea043]"
                  >
                    <DownloadIcon />
                    Download APK
                  </a>
                ) : (
                  <span className="inline-flex cursor-not-allowed items-center gap-2 rounded-md border border-[#30363d] bg-[#21262d] px-5 py-2.5 text-sm font-semibold text-[#8b949e]">
                    <DownloadIcon />
                    APK not published yet
                  </span>
                )}
                <a
                  href={source}
                  target="_blank"
                  rel="noreferrer"
                  className="focus-ring inline-flex items-center gap-2 rounded-md border border-[#30363d] bg-[#21262d] px-5 py-2.5 text-sm font-semibold text-[#f0f6fc] hover:bg-[#30363d]"
                >
                  <CodeIcon />
                  View source
                  <ExternalIcon className="h-3.5 w-3.5 text-[#8b949e]" />
                </a>
              </div>
            </div>

            <aside className="overflow-hidden rounded-lg border border-[#30363d] bg-[#0d1117] shadow-2xl shadow-black/20">
              <div className="border-b border-[#30363d] bg-[#161b22] px-4 py-3">
                <h2 className="text-sm font-semibold">Latest signed release</h2>
              </div>
              {release ? (
                <div className="space-y-4 p-4 text-sm">
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-[#8b949e]">Version</span>
                    <strong>v{release.versionName} ({release.versionCode})</strong>
                  </div>
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-[#8b949e]">Android</span>
                    <span>8.0+</span>
                  </div>
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-[#8b949e]">Size</span>
                    <span>{formatBytes(release.sizeBytes)}</span>
                  </div>
                  <div className="flex items-center justify-between gap-4">
                    <span className="text-[#8b949e]">Signed</span>
                    <span>{formatSignedAt(release.signedAt)}</span>
                  </div>
                  <div className="border-t border-[#21262d] pt-4">
                    <p className="mb-1 text-xs font-medium uppercase tracking-wide text-[#8b949e]">SHA-256</p>
                    <p className="code-value rounded-md bg-[#010409] p-2 text-xs leading-5 text-[#c9d1d9]">{release.sha256}</p>
                  </div>
                  <div className="border-t border-[#21262d] pt-4">
                    <p className="mb-1 text-xs font-medium uppercase tracking-wide text-[#8b949e]">APK signing certificate · SHA-256</p>
                    <p className="code-value rounded-md bg-[#010409] p-2 text-xs leading-5 text-[#c9d1d9]">{release.signingCertificateSha256}</p>
                    <p className="mt-2 text-xs leading-5 text-[#8b949e]">
                      Compare this fingerprint with the certificate reported by Android apksigner. For Google Play, the Play App Signing certificate may be different from the upload key.
                    </p>
                  </div>
                  <div className="flex items-center gap-2 text-xs text-[#3fb950]">
                    <ShieldIcon />
                    Signed and verified by the VPS release pipeline
                  </div>
                </div>
              ) : (
                <div className="p-5">
                  <p className="text-sm leading-6 text-[#8b949e]">
                    No signed APK has been published by the VPS yet. The source code remains available and buildable from GitHub.
                  </p>
                </div>
              )}
            </aside>
          </div>
        </div>
      </section>

      <section id="features" className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
        <div className="mb-8 max-w-2xl">
          <p className="mb-2 text-sm font-semibold text-[#58a6ff]">Built for mobile development</p>
          <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">A GitHub workflow that actually lives on Android.</h2>
          <p className="mt-3 leading-7 text-[#8b949e]">
            Nexora Git is not a WebView wrapper. The app combines Android-native UI, GitHub integrations and a local native Git engine.
          </p>
        </div>

        <div className="grid overflow-hidden rounded-lg border border-[#30363d] md:grid-cols-2">
          {features.map(([title, description], index) => (
            <article
              key={title}
              className={`p-6 ${index % 2 === 0 ? "md:border-r md:border-[#30363d]" : ""} ${index < 2 ? "border-b border-[#30363d]" : index === 2 ? "border-b border-[#30363d] md:border-b-0" : ""}`}
            >
              <div className="mb-3 flex items-center gap-2">
                <CheckIcon className="text-[#3fb950]" />
                <h3 className="font-semibold">{title}</h3>
              </div>
              <p className="text-sm leading-6 text-[#8b949e]">{description}</p>
            </article>
          ))}
        </div>
      </section>

      <section className="border-y border-[#21262d] bg-[#010409]">
        <div className="mx-auto grid max-w-7xl gap-8 px-4 py-14 sm:px-6 lg:grid-cols-3 lg:px-8">
          <div>
            <p className="mb-2 text-sm font-semibold text-[#58a6ff]">Technology</p>
            <h2 className="text-2xl font-bold">Native where it matters.</h2>
          </div>
          <div className="rounded-lg border border-[#30363d] bg-[#0d1117] p-5 lg:col-span-2">
            <div className="grid gap-x-8 gap-y-4 text-sm sm:grid-cols-2">
              {[
                ["Android", "Kotlin + Jetpack Compose"],
                ["Local Git", "libgit2 + C++17 + JNI"],
                ["GitHub", "REST + GraphQL + OAuth PKCE"],
                ["Storage", "Room + DataStore + SAF"],
                ["Security", "Android Keystore + self-hosted broker"],
                ["Release", "VPS build + persistent signing identity"],
              ].map(([key, value]) => (
                <div key={key} className="flex justify-between gap-4 border-b border-[#21262d] pb-3">
                  <span className="text-[#8b949e]">{key}</span>
                  <span className="text-right font-medium text-[#c9d1d9]">{value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section id="contribute" className="mx-auto max-w-7xl px-4 py-14 sm:px-6 lg:px-8">
        <div className="grid gap-6 lg:grid-cols-2">
          <article className="rounded-lg border border-[#30363d] bg-[#161b22] p-6">
            <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-full border border-[#30363d] bg-[#0d1117] text-[#f0f6fc]">
              <StarIcon />
            </div>
            <h2 className="text-xl font-bold">Contribute to Nexora Git</h2>
            <p className="mt-2 text-sm leading-6 text-[#8b949e]">
              Found a bug, want a feature, or can improve the Android, Git, security or VPS stack? Contributions are welcome when they preserve the project&apos;s security boundaries and mobile-first architecture.
            </p>
            <div className="mt-5 flex flex-wrap gap-3">
              <a className="focus-ring rounded-md bg-[#238636] px-4 py-2 text-sm font-semibold hover:bg-[#2ea043]" href={`${source}/blob/main/CONTRIBUTING.md`} target="_blank" rel="noreferrer">
                Contribution guide
              </a>
              <a className="focus-ring rounded-md border border-[#30363d] bg-[#21262d] px-4 py-2 text-sm font-semibold hover:bg-[#30363d]" href={`${source}/issues`} target="_blank" rel="noreferrer">
                Issues
              </a>
            </div>
          </article>

          <article className="rounded-lg border border-[#30363d] bg-[#161b22] p-6">
            <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-full border border-[#30363d] bg-[#0d1117] text-[#f0f6fc]">
              <CodeIcon />
            </div>
            <h2 className="text-xl font-bold">Credits</h2>
            <div className="mt-4 space-y-3 text-sm">
              <div className="flex items-center justify-between gap-4 border-b border-[#30363d] pb-3">
                <span className="text-[#8b949e]">Creator & maintainer</span>
                <a className="font-semibold text-[#58a6ff] hover:underline" href="https://github.com/Gh0stDeveloper" target="_blank" rel="noreferrer">Ghost Developer</a>
              </div>
              <div className="flex items-center justify-between gap-4 border-b border-[#30363d] pb-3">
                <span className="text-[#8b949e]">Community</span>
                <a className="font-semibold text-[#58a6ff] hover:underline" href={`${source}/graphs/contributors`} target="_blank" rel="noreferrer">Open-source contributors</a>
              </div>
              <div className="flex items-center justify-between gap-4">
                <span className="text-[#8b949e]">License</span>
                <a className="font-semibold text-[#58a6ff] hover:underline" href={`${source}/blob/main/LICENSE`} target="_blank" rel="noreferrer">Apache License 2.0</a>
              </div>
            </div>
          </article>
        </div>
      </section>
    </main>
  );
}
