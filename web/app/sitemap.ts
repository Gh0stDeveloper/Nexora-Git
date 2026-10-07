import type { MetadataRoute } from "next";
import { getSiteUrl } from "../lib/site";

export default function sitemap(): MetadataRoute.Sitemap {
  const site = getSiteUrl();
  return [
    { url: site, changeFrequency: "daily", priority: 1 },
    { url: `${site}/privacy`, changeFrequency: "monthly", priority: 0.6 },
    { url: `${site}/terms`, changeFrequency: "monthly", priority: 0.4 },
  ];
}
