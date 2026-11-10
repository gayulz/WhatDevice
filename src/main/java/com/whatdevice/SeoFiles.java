package com.whatdevice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable files for crawlers and AI agents: sitemap.xml, robots.txt,
 * llms.txt, llms-full.txt and devices.csv. Pages for humans are built elsewhere.
 *
 * <p>The sitemap carries no lastmod on purpose: every URL is regenerated on each build,
 * so a uniform build date would be inaccurate, and Google ignores lastmod once it learns
 * a site's values are unreliable.
 */
final class SeoFiles {

    /** Date the data snapshot was built, shown as the "as of" date in llms files. */
    static final String AS_OF = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();

    private SeoFiles() {
    }

    static void writeAll(List<BuildSite.Device> devices,
                         Map<String, List<BuildSite.Device>> byCategory) throws IOException {
        write("sitemap.xml", sitemap(devices, byCategory));
        write("robots.txt", "User-agent: *\nAllow: /\n\nSitemap: " + BuildSite.SITE_URL + "/sitemap.xml\n");
        write("llms.txt", llms(byCategory));
        write("llms-full.txt", llmsFull(byCategory));
        write("devices.csv", csv(devices));
    }

    static String sitemap(List<BuildSite.Device> devices, Map<String, List<BuildSite.Device>> byCategory) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        url(sb, BuildSite.SITE_URL + "/");
        ContentPages.sitemapUrls().forEach(loc -> url(sb, loc));
        CategoryPages.sitemapUrls(byCategory).forEach(loc -> url(sb, loc));
        for (BuildSite.Device d : devices) {
            url(sb, deviceUrl(d));
        }
        sb.append("</urlset>\n");
        return sb.toString();
    }

    /** Summary per llmstxt.org: H1, blockquote, then link sections. */
    static String llms(Map<String, List<BuildSite.Device>> byCategory) {
        String site = BuildSite.SITE_URL + "/";
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(BuildSite.SITE_NAME).append("\n\n");
        sb.append("> 애플 기기 머신 식별자(예: iPhone15,2)와 기종명(예: iPhone 14 Pro)의 대응표. ")
          .append("한국어 사이트이며 데이터 기준일은 ").append(AS_OF).append("이다.\n\n");
        sb.append("- 식별자의 숫자는 판매명 숫자와 다르다. iPhone15,2는 iPhone 15가 아니라 iPhone 14 Pro다.\n");
        sb.append("- 같은 기종명에 지역별로 식별자가 여러 개일 수 있다.\n");
        sb.append("- 설정 앱의 A로 시작하는 모델 번호는 이 표의 식별자와 다른 체계다.\n");
        sb.append("- ").append(BuildSite.DISCLAIMER).append("\n\n");
        sb.append("## 데이터\n\n");
        sb.append("- [전체 대응표 (텍스트)](").append(site).append("llms-full.txt): 식별자·기종명·한글 표기 전체\n");
        sb.append("- [전체 대응표 (CSV)](").append(site).append("devices.csv): identifier,name,korean_name,category,url\n\n");
        sb.append("## 카테고리별 목록\n\n");
        for (Map.Entry<String, List<BuildSite.Device>> e : byCategory.entrySet()) {
            sb.append("- [").append(e.getKey()).append(" 식별자 ").append(e.getValue().size()).append("종](")
              .append(BuildSite.SITE_URL).append("/category/").append(CategoryPages.slugOf(e.getKey()))
              .append(".html)\n");
        }
        sb.append("\n## 가이드\n\n");
        for (String[] p : ContentPages.PAGES) {
            if (p[0].startsWith("guide/")) {
                sb.append("- [").append(p[1]).append("](").append(site).append(p[0]).append("): ")
                  .append(p[2]).append("\n");
            }
        }
        return sb.toString();
    }

    /** Full mapping in one fetch, so an agent can answer without crawling 200+ pages. */
    static String llmsFull(Map<String, List<BuildSite.Device>> byCategory) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(BuildSite.SITE_NAME).append(" — 애플 기기 식별자 전체 대응표\n\n");
        sb.append("> 기준일 ").append(AS_OF).append(". 형식: 식별자 — 기종명 (한글 표기). ")
          .append(BuildSite.DISCLAIMER).append("\n");
        for (Map.Entry<String, List<BuildSite.Device>> e : byCategory.entrySet()) {
            sb.append("\n## ").append(e.getKey()).append(" (").append(e.getValue().size()).append("종)\n\n");
            for (BuildSite.Device d : e.getValue()) {
                sb.append("- ").append(d.identifier).append(" — ").append(d.name)
                  .append(" (").append(d.koreanName).append(") ").append(deviceUrl(d)).append("\n");
            }
        }
        return sb.toString();
    }

    static String csv(List<BuildSite.Device> devices) {
        StringBuilder sb = new StringBuilder("identifier,name,korean_name,category,url\n");
        for (BuildSite.Device d : devices) {
            sb.append(cell(d.identifier)).append(',').append(cell(d.name)).append(',')
              .append(cell(d.koreanName)).append(',').append(cell(d.category)).append(',')
              .append(cell(deviceUrl(d))).append('\n');
        }
        return sb.toString();
    }

    /** RFC 4180: identifiers like "iPhone15,2" contain commas, so every cell is quoted. */
    static String cell(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String deviceUrl(BuildSite.Device d) {
        return BuildSite.SITE_URL + "/device/" + d.slug + ".html";
    }

    private static void url(StringBuilder sb, String loc) {
        sb.append("  <url><loc>").append(BuildSite.esc(loc)).append("</loc></url>\n");
    }

    private static void write(String name, String body) throws IOException {
        Files.write(BuildSite.DIST.resolve(name), body.getBytes(StandardCharsets.UTF_8));
    }
}
