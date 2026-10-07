import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Return to Nexora Git",
  robots: {
    index: false,
    follow: false,
  },
};

export default function AndroidOAuthCallbackPage() {
  return (
    <main className="mx-auto flex min-h-[70vh] max-w-2xl items-center px-4 py-16 sm:px-6">
      <section className="w-full rounded-lg border border-[#30363d] bg-[#161b22] p-6 sm:p-8">
        <p className="text-sm font-semibold text-[#58a6ff]">Nexora Git authentication</p>
        <h1 className="mt-2 text-2xl font-bold tracking-tight">Return to the Android app</h1>
        <p className="mt-4 leading-7 text-[#8b949e]">
          This address is reserved for the verified Nexora Git Android App Link. If the app is installed
          and the signing association is valid, Android opens Nexora Git automatically.
        </p>
        <p className="mt-3 text-sm leading-6 text-[#8b949e]">
          If you remain on this page, return to Nexora Git and retry sign-in after confirming you installed
          an official signed build.
        </p>
      </section>
    </main>
  );
}
