package com.whatdevice;

import java.util.ArrayList;
import java.util.List;

/**
 * 구조화 데이터(JSON-LD)·검색엔진 소유확인 메타·기기 상세의 검색용 문구 생성 담당.
 *
 * <p>JSON-LD 는 body 안에 넣어도 구글·빙이 읽는다. renderLayout 시그니처를 늘리지 않으려고
 * 각 페이지의 main 끝에 붙인다. 화면의 FAQ 와 FAQPage 가 어긋나지 않도록 문구는 한곳에서 만든다
 * (구글은 구조화 데이터가 화면에 보이는 내용과 다르면 무시한다).
 */
final class SeoMarkup {

    /** 네이버 서치어드바이저 소유확인 코드. 비어 있으면 메타를 만들지 않는다. */
    static final String NAVER_VERIFICATION = "";
    /** Bing 웹마스터 도구 소유확인 코드. 비어 있으면 메타를 만들지 않는다. */
    static final String BING_VERIFICATION = "";

    /** 계열 문장에 나열할 최대 개수. 넘치면 "외 N종"으로 줄인다. */
    private static final int FAMILY_LIST_MAX = 8;

    private SeoMarkup() {
    }

    // ===================== 소유확인 =====================

    /** layout.html 의 {{SITE_VERIFICATION}} 자리에 들어갈 메타. 코드가 없으면 빈 문자열. */
    static String verificationMetas() {
        StringBuilder sb = new StringBuilder();
        if (!NAVER_VERIFICATION.isEmpty()) {
            sb.append("<meta name=\"naver-site-verification\" content=\"")
              .append(BuildSite.esc(NAVER_VERIFICATION)).append("\">\n");
        }
        if (!BING_VERIFICATION.isEmpty()) {
            sb.append("<meta name=\"msvalidate.01\" content=\"")
              .append(BuildSite.esc(BING_VERIFICATION)).append("\">\n");
        }
        return sb.toString();
    }

    // ===================== JSON-LD =====================

    /** 노드들을 하나의 @graph 스크립트로 묶는다. */
    static String script(String... nodes) {
        return "<script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@graph\":["
                + String.join(",", nodes) + "]}</script>\n";
    }

    /**
     * 사이트 이름과 검색 동작. 구글은 사이트링크 검색창 표시를 2024년에 중단했으므로
     * SearchAction 은 구글 화면 효과를 기대하지 않는다(다른 파서용).
     */
    static String webSite() {
        return "{\"@type\":\"WebSite\",\"name\":" + BuildSite.js(BuildSite.SITE_NAME)
                + ",\"url\":" + BuildSite.js(BuildSite.SITE_URL + "/")
                + ",\"inLanguage\":\"ko\""
                + ",\"potentialAction\":{\"@type\":\"SearchAction\",\"target\":"
                + BuildSite.js(BuildSite.SITE_URL + "/?q={search_term_string}")
                + ",\"query-input\":\"required name=search_term_string\"}}";
    }

    /** 화면의 breadcrumb 과 같은 경로. 각 원소는 {이름, 절대 URL}. */
    static String breadcrumb(String[]... trail) {
        StringBuilder sb = new StringBuilder("{\"@type\":\"BreadcrumbList\",\"itemListElement\":[");
        for (int i = 0; i < trail.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"@type\":\"ListItem\",\"position\":").append(i + 1)
              .append(",\"name\":").append(BuildSite.js(trail[i][0]))
              .append(",\"item\":").append(BuildSite.js(trail[i][1])).append('}');
        }
        return sb.append("]}").toString();
    }

    /** 구글 FAQ 리치 결과는 정부·보건 사이트로 한정돼 있어, 다른 검색엔진·AI 파서용이다. */
    static String faqPage(List<String[]> faq) {
        StringBuilder sb = new StringBuilder("{\"@type\":\"FAQPage\",\"mainEntity\":[");
        for (int i = 0; i < faq.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"@type\":\"Question\",\"name\":").append(BuildSite.js(plain(faq.get(i)[0])))
              .append(",\"acceptedAnswer\":{\"@type\":\"Answer\",\"text\":")
              .append(BuildSite.js(plain(faq.get(i)[1]))).append("}}");
        }
        return sb.append("]}").toString();
    }

    /** 화면용 HTML 에서 태그를 걷어 JSON-LD 의 텍스트로 쓴다. */
    private static String plain(String html) {
        return html.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&");
    }

    // ===================== 기기 상세 문구 =====================

    /** 한글 표기가 영문명과 다를 때만 "(아이폰 14 프로)" 형태로 돌려준다. 이스케이프 전 원문. */
    static String koreanParen(BuildSite.Device d) {
        return d.koreanName.equals(d.name) ? "" : "(" + d.koreanName + ")";
    }

    /**
     * 식별자 → 기종명을 한 문장으로 답한다. 조사가 식별자 끝 글자(숫자)에 따라 달라지는 문제를
     * 피하려고 "의"와 "기종명은"만 쓴다. 화면 첫 문단과 FAQ 첫 답이 같은 문장을 쓴다.
     */
    static String definitionHtml(BuildSite.Device d) {
        return "식별자 <code class=\"mono\">" + BuildSite.esc(d.identifier) + "</code>의 기종명은 <strong>"
                + BuildSite.esc(d.name) + "</strong>" + BuildSite.esc(koreanParen(d)) + "입니다.";
    }

    static String title(BuildSite.Device d) {
        return d.identifier + " 기종명 - " + d.name + koreanParen(d) + " | " + BuildSite.SITE_NAME;
    }

    static String description(BuildSite.Device d, int familySize) {
        String family = familySize > 1
                ? " 같은 " + d.familyKey + ",x 계열 " + familySize + "종의 목록과"
                : "";
        return "모델 식별자 " + d.identifier + "의 기종명은 " + d.name + koreanParen(d) + "입니다."
                + family + " 로그에서 식별자로 기종을 확인하는 방법도 정리했습니다.";
    }

    /** 기기 상세 FAQ. 각 원소는 {질문, 답변 HTML}. 계열에 다른 기기가 있으면 3번째 문항이 붙는다. */
    static List<String[]> deviceFaq(BuildSite.Device d, List<BuildSite.Device> family, String base) {
        String id = BuildSite.esc(d.identifier);
        List<String[]> faq = new ArrayList<>();
        faq.add(new String[]{id + " 식별자는 어떤 기종인가요?",
                definitionHtml(d) + " 앱 크래시 로그, 애널리틱스, 일부 진단 화면 등에서 기종명 대신 이 식별자가 표시됩니다."});
        faq.add(new String[]{id + " 식별자는 어디서 확인할 수 있나요?",
                "크래시 리포트의 기기 정보 필드, 모바일 애널리틱스의 디바이스 모델 항목, 또는 "
                + "<code class=\"mono\">uname</code> / <code class=\"mono\">sysctl hw.machine</code> 계열 시스템 정보에서 볼 수 있습니다. "
                + "자세한 방법은 <a href=\"" + base + "guide/find-from-logs.html\">로그에서 기기 모델 확인하는 법</a> 가이드를 참고하세요."});
        if (family != null && family.size() > 1) {
            faq.add(new String[]{BuildSite.esc(d.familyKey) + ",x 계열에는 어떤 기기가 있나요?",
                    "식별자 앞부분이 같아 한 계열로 묶이는 기기는 " + familyList(family) + "입니다."});
        }
        return faq;
    }

    private static String familyList(List<BuildSite.Device> family) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(family.size(), FAMILY_LIST_MAX);
        for (int i = 0; i < shown; i++) {
            BuildSite.Device f = family.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("<code class=\"mono\">").append(BuildSite.esc(f.identifier)).append("</code> ")
              .append(BuildSite.esc(f.name));
        }
        if (family.size() > shown) {
            sb.append(" 외 ").append(family.size() - shown).append("종");
        }
        return sb.toString();
    }

    static String faqHtml(List<String[]> faq) {
        StringBuilder sb = new StringBuilder("      <section class=\"faq\">\n        <h2>자주 묻는 질문</h2>\n");
        for (String[] qa : faq) {
            sb.append("        <h3>").append(qa[0]).append("</h3>\n")
              .append("        <p>").append(qa[1]).append("</p>\n");
        }
        return sb.append("      </section>\n").toString();
    }
}
