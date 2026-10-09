import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy | Nexora Git",
  description: "Data handling information for Nexora Git Android and its official website.",
};

const source = "https://github.com/Gh0stDeveloper/Nexora-Git";

export default function PrivacyPolicyPage() {
  return (
    <main className="mx-auto min-h-screen max-w-3xl px-5 py-12 text-[#c9d1d9] sm:px-8">
      <a className="text-sm text-[#58a6ff] hover:underline" href="/">← Nexora Git</a>
      <h1 className="mt-8 text-3xl font-bold text-[#f0f6fc]">Privacy Policy</h1>
      <p className="mt-3 text-sm text-[#8b949e]">
        Scope: Nexora Git Android application and the official project website. This page is
        an implementation draft pending review of the deployed production infrastructure.
      </p>
      <div className="mt-9 space-y-7 text-sm leading-7">
        <section>
          <h2 className="text-lg font-semibold text-[#f0f6fc]">Data stored on your device</h2>
          <p>App preferences, account and workspace metadata, selected repositories and
            source files are stored locally. GitHub authentication tokens are designed to be
            encrypted with keys backed by Android Keystore. App backups are disabled for
            sensitive state.</p>
        </section>
        <section>
          <h2 className="text-lg font-semibold text-[#f0f6fc]">GitHub and authorization</h2>
          <p>When you sign in, GitHub handles its own authorization web flow. The optional
            self-hosted Auth Broker exchanges authorization codes and refreshes tokens.
            GitHub receives data needed for user-requested GitHub operations such as
            cloning private repositories, pushing commits, creating issues and uploading files.</p>
        </section>
        <section>
          <h2 className="text-lg font-semibold text-[#f0f6fc]">Website and logs</h2>
          <p>Normal HTTPS hosting infrastructure may process technical request metadata
            such as IP addresses, timestamps, paths and error codes for operation and
            security. Exact production log retention will be confirmed before the site and
            app are publicly launched.</p>
        </section>
        <section>
          <h2 className="text-lg font-semibold text-[#f0f6fc]">Advertising and analytics</h2>
          <p>The currently documented app baseline does not include an advertising SDK
            or third-party analytics SDK. The actual production bundle will be audited again
            before a public store declaration is submitted.</p>
        </section>
        <section>
          <h2 className="text-lg font-semibold text-[#f0f6fc]">Your choices and deletion</h2>
          <p>You can sign out to remove locally stored tokens and remove local repositories
            or application data from your device. Data already sent to GitHub is managed
            through your GitHub account and the provider&apos;s policies.</p>
        </section>
        <section>
          <h2 className="text-lg font-semibold text-[#f0f6fc]">Privacy questions and updates</h2>
          <p>Review the <a className="text-[#58a6ff] underline" href={source + "/blob/main/docs/PRIVACY.md"}>open-source privacy document</a>.
            For a non-sensitive question, contact the project through its{" "}
            <a className="text-[#58a6ff] underline" href={source + "/discussions"}>GitHub Discussions</a>.
            Report vulnerabilities privately using the{" "}
            <a className="text-[#58a6ff] underline" href={source + "/security/policy"}>security policy</a>.
            Material privacy changes will be documented before new store releases.</p>
        </section>
      </div>
      <a className="mt-10 inline-block text-sm text-[#58a6ff] underline" href="/">Back to home</a>
    </main>
  );
}
