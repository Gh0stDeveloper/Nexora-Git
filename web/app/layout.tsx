import type { Metadata } from "next";
import "./globals.css";
import { Footer } from "../components/footer";
import { SiteHeader } from "../components/site-header";
import { getSiteUrl } from "../lib/site";

const description =
  "Nexora Git is a native open-source Git and GitHub workspace for Android with a real local Git engine, code editing, GitHub workflows and self-hosted signed releases.";

const siteUrl = getSiteUrl();

const structuredData = {
  "@context": "https://schema.org",
  "@type": "SoftwareApplication",
  name: "Nexora Git",
  applicationCategory: "DeveloperApplication",
  operatingSystem: "Android 8.0+",
  description,
  url: siteUrl,
  downloadUrl: new URL("/download/nexora-git.apk", siteUrl).toString(),
  codeRepository: "https://github.com/Gh0stDeveloper/Nexora-Git",
  license: "https://www.apache.org/licenses/LICENSE-2.0",
  author: {
    "@type": "Person",
    name: "Ghost Developer",
    url: "https://github.com/Gh0stDeveloper",
  },
  isAccessibleForFree: true,
};

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "Nexora Git — Git & GitHub workspace for Android",
    template: "%s · Nexora Git",
  },
  description,
  applicationName: "Nexora Git",
  authors: [{ name: "Ghost Developer", url: "https://github.com/Gh0stDeveloper" }],
  creator: "Ghost Developer",
  publisher: "Nexora Git",
  keywords: ["Nexora Git", "Git", "GitHub", "Android", "Kotlin", "libgit2", "open source"],
  alternates: { canonical: "/" },
  openGraph: {
    type: "website",
    url: "/",
    siteName: "Nexora Git",
    title: "Nexora Git — Git & GitHub workspace for Android",
    description,
    images: [{ url: "/opengraph-image", width: 1200, height: 630, alt: "Nexora Git for Android" }],
  },
  twitter: {
    card: "summary_large_image",
    title: "Nexora Git — Git & GitHub workspace for Android",
    description,
    images: ["/opengraph-image"],
  },
  robots: {
    index: true,
    follow: true,
  },
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>
        <script
          type="application/ld+json"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify(structuredData).replace(/</g, "\\u003c"),
          }}
        />
        <SiteHeader />
        {children}
        <Footer />
      </body>
    </html>
  );
}
