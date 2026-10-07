import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description: "Privacy and data-handling policy for Nexora Git.",
  alternates: { canonical: "/privacy" },
};

const source = "https://github.com/Gh0stDeveloper/Nexora-Git";

export default function PrivacyPage() {
  return (
    <main className="mx-auto max-w-4xl px-4 py-12 sm:px-6 lg:px-8">
      <div className="mb-8 border-b border-[#30363d] pb-6">
        <p className="mb-2 text-sm font-semibold text-[#58a6ff]">Nexora Git</p>
        <h1 className="text-3xl font-bold tracking-tight">Privacy Policy</h1>
        <p className="mt-3 text-sm text-[#8b949e]">Last updated: October 7, 2026</p>
      </div>

      <div className="space-y-8 text-[15px] leading-7 text-[#c9d1d9]">
        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Data processed on your device</h2>
          <p>
            Nexora Git may store GitHub account metadata, encrypted GitHub access and refresh tokens,
            repository and workspace metadata, repositories and files you explicitly clone or import,
            and application settings. Authentication tokens are protected with Android Keystore-backed
            encryption and Android application backup is disabled for sensitive app data.
          </p>
        </section>

        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Network communication</h2>
          <p>
            Nexora Git communicates with GitHub when you request GitHub operations. A small self-hosted
            Auth Broker handles the confidential portion of GitHub App OAuth. The broker is not a
            source-code proxy and is designed not to retain repository contents.
          </p>
        </section>

        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Analytics and advertising</h2>
          <p>
            The production baseline contains no advertising SDK and no behavioral analytics SDK.
            Normal web-server and reverse-proxy logs may contain technical request data required for
            security, diagnostics and service operation.
          </p>
        </section>

        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Source code and local files</h2>
          <p>
            Repository contents remain on your device unless you explicitly perform an operation that
            sends data to GitHub or another destination, such as push, issue or comment creation,
            release upload, sharing or file export.
          </p>
        </section>

        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Sale of data</h2>
          <p>Nexora Git does not sell user data.</p>
        </section>

        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Accounts and retention</h2>
          <p>
            Nexora Git does not create or host a first-party user account. GitHub authentication
            connects an existing GitHub account. The Auth Broker is designed to process OAuth
            exchange and refresh requests without retaining repository contents or maintaining a
            Nexora Git account database. Local tokens remain on the device until sign-out, app-data
            removal or Android uninstallation.
          </p>
        </section>

        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Deletion</h2>
          <p>
            Signing out removes locally stored authentication tokens for the account. You can also
            remove local workspaces or application data from your device. Data already sent to GitHub
            remains governed by your GitHub account and repository controls.
          </p>
        </section>

        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Contact and privacy inquiries</h2>
          <p>
            For non-sensitive privacy questions, use the{" "}
            <a
              className="text-[#58a6ff] hover:underline"
              href={source + "/issues"}
              target="_blank"
              rel="noreferrer"
            >
              public issue tracker
            </a>.
            Do not include access tokens or private repository content. Security-sensitive reports
            must follow the private reporting process in SECURITY.md.
          </p>
        </section>

        <section>
          <h2 className="mb-2 text-xl font-semibold text-[#f0f6fc]">Open-source transparency</h2>
          <p>
            The authoritative implementation and version history are available in the{" "}
            <a
              className="text-[#58a6ff] hover:underline"
              href={source}
              target="_blank"
              rel="noreferrer"
            >
              public source repository
            </a>.
          </p>
        </section>
      </div>
    </main>
  );
}
