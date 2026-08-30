package com.whatdevice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리 허브 페이지(dist/category/{슬러그}.html) 생성 담당.
 *
 * <p>존재 이유는 SEO 다. 홈은 검색창만 두고 결과를 JS 로 그리므로, 크롤러가 받는 HTML 에는
 * 기기 상세 페이지로 가는 링크가 하나도 없었다. 그 결과 232개 기기 페이지가 sitemap 에만 있고
 * 내부 링크상 고아가 되어 색인되지 않았다(GSC 확인: "참조 페이지 감지된 페이지 없음").
 *
 * <p>이 클래스의 책임은 홈 → 카테고리 허브 → 기기 상세 로 이어지는 크롤 경로를 만드는 것 하나다.
 * 기기 상세 페이지 생성은 BuildSite 가 계속 맡는다.
 */
final class CategoryPages {

    /** 홈에서 카테고리마다 미리 보여줄 기기 수. 나머지는 허브 페이지가 전부 나열한다. */
    static final int HOME_PREVIEW = 12;

    private CategoryPages() {
    }

    /** 카테고리명 → URL 슬러그. "iPhone" → "iphone" */
    static String slugOf(String category) {
        return category.toLowerCase();
    }

    /** base(상대경로 접두어) 기준 카테고리 허브 링크. */
    static String href(String base, String category) {
        return base + "category/" + slugOf(category) + ".html";
    }

    /**
     * 기기를 카테고리별로 묶는다. BuildSite.CATEGORIES 선언 순서를 따르고,
     * 해당 기기가 하나도 없는 카테고리는 빈 허브가 생기지 않도록 제외한다.
     */
    static Map<String, List<BuildSite.Device>> groupByCategory(List<BuildSite.Device> devices) {
        Map<String, List<BuildSite.Device>> map = new LinkedHashMap<>();
        for (String category : BuildSite.CATEGORIES) {
            List<BuildSite.Device> list = new ArrayList<>();
            for (BuildSite.Device d : devices) {
                if (d.category.equals(category)) {
                    list.add(d);
                }
            }
            if (!list.isEmpty()) {
                map.put(category, list);
            }
        }
        return map;
    }

    /**
     * 기기 상세로 가는 &lt;li&gt; 링크 목록을 만든다.
     *
     * @param base  상대경로 접두어 (홈은 "", category/ 안은 "../")
     * @param limit 0 이하면 전체, 그 외에는 앞에서 limit 개까지만
     */
    static String deviceLinks(List<BuildSite.Device> devices, String base, int limit) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (BuildSite.Device d : devices) {
            if (limit > 0 && count >= limit) {
                break;
            }
            sb.append("<li><a href=\"").append(base).append("device/").append(d.slug).append(".html\">")
              .append("<code class=\"mono\">").append(BuildSite.esc(d.identifier)).append("</code> ")
              .append(BuildSite.esc(d.name)).append("</a></li>\n");
            count++;
        }
        return sb.toString();
    }

    /**
     * 모든 페이지 푸터에 들어가는 카테고리 링크 모음.
     * 기기 상세 페이지 전부가 허브를 가리키게 되어 링크 그래프가 양방향으로 닫힌다.
     */
    static String footerNav(String base) {
        StringBuilder sb = new StringBuilder();
        for (String category : BuildSite.CATEGORIES) {
            sb.append("<a href=\"").append(href(base, category)).append("\">")
              .append(BuildSite.esc(category)).append("</a>\n");
        }
        return sb.toString();
    }

    /** 홈에 넣을 카테고리 섹션들. 카테고리마다 최신 HOME_PREVIEW 개를 링크로 노출한다. */
    static String homeSections(Map<String, List<BuildSite.Device>> byCategory) {
        StringBuilder sb = new StringBuilder();
        // id=directory: search.js 가 검색 중에만 숨긴다. 검색어가 비면 이 정적 목록이 기본 화면이며,
        // 크롤러가 JS 없이 받는 화면과 사용자가 처음 보는 화면이 같아진다.
        sb.append("<div id=\"directory\" class=\"device-directory\">\n");
        for (Map.Entry<String, List<BuildSite.Device>> e : byCategory.entrySet()) {
            String category = BuildSite.esc(e.getKey());
            List<BuildSite.Device> list = e.getValue();
            String hubHref = href("", e.getKey());
            sb.append("<section class=\"home-category\">\n")
              .append("  <h2><a href=\"").append(hubHref).append("\">").append(category)
              .append("</a> <span class=\"muted\">").append(list.size()).append("종</span></h2>\n")
              .append("  <ul class=\"related-list device-index\">\n")
              .append(deviceLinks(list, "", HOME_PREVIEW))
              .append("  </ul>\n")
              .append("  <p class=\"more\"><a href=\"").append(hubHref).append("\">")
              .append(category).append(" 전체 ").append(list.size()).append("종 보기 →</a></p>\n")
              .append("</section>\n");
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    /** 카테고리 허브 페이지를 카테고리 수만큼 생성한다. */
    static void generate(Map<String, List<BuildSite.Device>> byCategory, String layout)
            throws IOException {
        Path dir = BuildSite.DIST.resolve("category");
        Files.createDirectories(dir);
        for (Map.Entry<String, List<BuildSite.Device>> e : byCategory.entrySet()) {
            String category = BuildSite.esc(e.getKey());
            List<BuildSite.Device> list = e.getValue();

            String main =
                    "<nav class=\"breadcrumb\" aria-label=\"위치\">\n"
                  + "  <a href=\"../index.html\">홈</a>\n"
                  + "  <span class=\"sep\">›</span>\n"
                  + "  <span>" + category + "</span>\n"
                  + "</nav>\n"
                  + "<h1>" + category + " 기기 식별자 전체 목록</h1>\n"
                  + "<p class=\"lead\">" + category + " 계열 " + list.size()
                  + "종의 애플 모델 식별자와 기종명입니다. 항목을 누르면 상세 정보로 이동합니다.</p>\n"
                  + "<ul class=\"related-list device-index\">\n"
                  + deviceLinks(list, "../", 0)
                  + "</ul>\n"
                  + BuildSite.adSlotHtml(2);

            String title = e.getKey() + " 기기 식별자 전체 목록 (" + list.size() + "종) | "
                    + BuildSite.SITE_NAME;
            String desc = e.getKey() + " 계열 애플 모델 식별자 " + list.size()
                    + "종을 기종명과 함께 정리한 목록입니다.";
            String canonical = BuildSite.SITE_URL + "/category/" + slugOf(e.getKey()) + ".html";

            String html = BuildSite.renderLayout(layout, "../", title, desc, canonical, main, "");
            Files.write(dir.resolve(slugOf(e.getKey()) + ".html"),
                    html.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** sitemap 에 넣을 허브 URL 목록. */
    static List<String> sitemapUrls(Map<String, List<BuildSite.Device>> byCategory) {
        List<String> urls = new ArrayList<>();
        for (String category : byCategory.keySet()) {
            urls.add(BuildSite.SITE_URL + "/category/" + slugOf(category) + ".html");
        }
        return urls;
    }
}
