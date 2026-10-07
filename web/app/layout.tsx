import type { Metadata } from "next";
import "./globals.css";
import { Footer } from "../components/footer";
import { SiteHeader } from "../components/site-header";
import { getSiteUrl } from "../lib/site";

const description =
  "Nexora Git is a native open-source Git and GitHub workspace for Android with a real local Git engine, code editing, GitHub workflows and self-hosted signed releases.";

const siteUrl = getSiteUrl();

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
        <SiteHeader />
        {children}
        <Footer />
      </body>
    </html>
  );
}
