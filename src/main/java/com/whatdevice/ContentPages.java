package com.whatdevice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written pages (guides, policies, 404) wrapped in the shared layout.
 * Body HTML lives in content/; this class only knows the page list and the wrapping rules.
 */
final class ContentPages {

    /** {output path, title, description, content file}. Guides get ads and breadcrumbs; policies do not. */
    static final String[][] PAGES = {
        {"guide/identifier-basics.html", "애플 기기 식별자(모델 코드)란 무엇인가",
                "iPhone15,2 같은 애플 기기 식별자가 무엇이고 어디에 쓰이는지 쉽게 설명합니다.",
                "guide-identifier-basics.html"},
        {"guide/find-from-logs.html", "크래시 로그·애널리틱스에서 기기 모델 확인하는 법",
                "크래시 로그나 애널리틱스에 찍힌 기기 식별자로 실제 기종을 알아내는 방법을 안내합니다.",
                "guide-find-from-logs.html"},
        {"guide/iphone-model-check.html", "내 아이폰 모델 확인하는 법: 모델 번호와 식별자의 차이",
                "설정 앱에서 아이폰 모델 번호를 확인하는 경로와, A로 시작하는 모델 번호와 iPhone15,2 같은 식별자가 어떻게 다른지 정리했습니다.",
                "guide-iphone-model-check.html"},
        {"policy/terms.html", "이용약관", BuildSite.SITE_NAME + " 이용약관입니다.", "policy-terms.html"},
        {"policy/privacy.html", "개인정보처리방침", BuildSite.SITE_NAME + " 개인정보처리방침입니다.", "policy-privacy.html"},
        {"policy/contact.html", "문의", BuildSite.SITE_NAME + " 문의 안내입니다.", "policy-contact.html"},
    };

    private ContentPages() {
    }

    /** Absolute URLs of every content page, for the sitemap. */
    static List<String> sitemapUrls() {
        List<String> urls = new ArrayList<>();
        for (String[] p : PAGES) {
            urls.add(BuildSite.SITE_URL + "/" + p[0]);
        }
        return urls;
    }

    static void generate(String layout) throws IOException {
        for (String[] p : PAGES) {
            boolean guide = p[0].startsWith("guide/");
            String body = BuildSite.readContent(p[3])
                    .replace("{{BASE}}", "../")
                    .replace("{{EMAIL}}", BuildSite.CONTACT_EMAIL)
                    .replace("{{SITE_NAME}}", BuildSite.SITE_NAME)
                    // Policy pages carry no ads to protect trust; their files have no tokens anyway.
                    .replace("{{COUPANG}}", guide ? Ads.coupangHtml() : "")
                    .replace("{{AD_MID}}", guide ? Ads.adSlotHtml(1) : "");
            String canonical = BuildSite.SITE_URL + "/" + p[0];
            if (guide) {
                body = Ads.adSlotHtml(0) + "\n" + body + "\n" + Ads.adSlotHtml(2)
                        + SeoMarkup.script(SeoMarkup.breadcrumb(new String[]{"홈", BuildSite.SITE_URL + "/"},
                                new String[]{p[1], canonical}));
            }
            String html = BuildSite.renderLayout(layout, "../", p[1] + " | " + BuildSite.SITE_NAME,
                    p[2], canonical, body, "");
            write(p[0], html);
        }
    }

    /**
     * GitHub Pages serves this file for any missing path, at any depth, with status 404.
     * Relative links would break there, so the base is the absolute site root.
     */
    static void generateNotFound(String layout) throws IOException {
        String base = BuildSite.SITE_URL + "/";
        String main = "<article class=\"content\">\n"
                + "  <h1>페이지를 찾을 수 없습니다</h1>\n"
                + "  <p>주소가 바뀌었거나 없는 페이지입니다. 식별자를 찾고 있었다면 "
                + "<a href=\"" + base + "\">검색 페이지</a>에서 <code class=\"mono\">iPhone15,2</code>처럼 입력해 보세요.</p>\n"
                + "  <ul>\n"
                + "    <li><a href=\"" + base + "category/iphone.html\">iPhone 식별자 전체 목록</a></li>\n"
                + "    <li><a href=\"" + base + "category/ipad.html\">iPad 식별자 전체 목록</a></li>\n"
                + "    <li><a href=\"" + base + "guide/iphone-model-check.html\">내 아이폰 모델 확인하는 법</a></li>\n"
                + "  </ul>\n"
                + "</article>\n";
        String html = BuildSite.renderLayout(layout, base, "페이지를 찾을 수 없습니다 | " + BuildSite.SITE_NAME,
                "요청한 페이지가 없습니다.", base, main, "");
        // A 404 must not claim the home page as its canonical.
        html = html.replace("<link rel=\"canonical\" href=\"" + base + "\">",
                "<meta name=\"robots\" content=\"noindex\">");
        write("404.html", html);
    }

    private static void write(String relative, String html) throws IOException {
        Path out = BuildSite.DIST.resolve(relative);
        Files.createDirectories(out.getParent());
        Files.write(out, html.getBytes(StandardCharsets.UTF_8));
    }
}
