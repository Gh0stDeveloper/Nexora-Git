import type { MetadataRoute } from "next";

export default function sitemap(): MetadataRoute.Sitemap {
  const site = process.env.NEXT_PUBLIC_SITE_URL ?? "https://nexora-git.invalid";
  return [
    { url: site, changeFrequency: "daily", priority: 1 },
    { url: `${site}/privacy`, changeFrequency: "monthly", priority: 0.7 },
    { url: `${site}/terms`, changeFrequency: "monthly", priority: 0.4 },
  ];
}
