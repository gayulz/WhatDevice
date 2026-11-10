package com.whatdevice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * WhatDevice 정적 사이트 빌드 스크립트.
 *
 * <p>data/devices.txt 를 읽어 필터·가공한 뒤 templates/ 와 static/ 을 이용해
 * dist/ 에 정적 HTML 사이트 전체를 생성한다. 서버가 아니라 한 번 실행하고 끝나는
 * "빌드 도구"다.
 *
 * <p>처리 흐름은 main() 을 위에서 아래로 읽으면 그대로 파악되도록 함수 단위로 분리했다.
 */
public class BuildSite {

    // ===================== 설정 상수 (바꿀 값은 전부 여기에 모음) =====================

    /** 사이트 브랜드명. */
    static final String SITE_NAME = "WhatDevice";
    /** 사이트 한 줄 설명. */
    static final String SITE_DESC = "애플 기기 식별자(모델 코드)와 기종명을 양방향으로 변환·검색하는 도구";
    /**
     * 배포 도메인(절대 URL). sitemap·canonical·OG 에 쓰인다.
     * 실제 배포 주소가 정해지면 이 값만 교체하면 된다. 끝에 슬래시 없이.
     */
    static final String SITE_URL = "https://what-device.store";
    /** 문의 이메일 (지시서 2번 항목 5번 확정값). */
    static final String CONTACT_EMAIL = "gayulz@kakao.com";
    /** 면책 문구 (누락 금지). */
    static final String DISCLAIMER = "비공식 참고용 데이터입니다. 출처: adamawolf/apple_device_identifiers (gist 3048717), DeviceKit·UFADE 보강 자료";

    /**
     * 사이트에 포함할 기기 카테고리(=식별자 접두어). 여기에 "iPod","Watch","Mac" 등을
     * 추가하면 범위가 바로 넓어진다. (지시서: 카테고리 토글로 확장 가능하게 설계)
     */
    static final List<String> CATEGORIES = List.of("iPhone", "iPad", "Mac", "Watch", "iPod", "Simulator");

    // 입출력 경로 (프로젝트 루트 기준 상대경로 — build.gradle 에서 workingDir 를 루트로 고정)
    static final Path DATA = Paths.get("data");
    static final Path TEMPLATES = Paths.get("templates");
    static final Path STATIC = Paths.get("static");
    static final Path CONTENT = Paths.get("content");
    static final Path DIST = Paths.get("dist");

    // ===================== 도메인 모델 =====================

    /** 기기 1종을 표현. (record 대신 일반 클래스 — 폭넓은 Java 버전 호환) */
    static final class Device {
        final String name;        // 기종명 (예: iPhone 14 Pro)
        final String identifier;  // 식별자 (예: iPhone15,2)
        final String slug;        // URL 슬러그 (예: iphone15-2)
        final String category;    // 카테고리 (예: iPhone)
        final String koreanName;  // 한글 표기(검색·참고용, 자동 생성)
        final String familyKey;   // 같은 하드웨어 계열 묶음 키 (예: iPhone15)

        Device(String name, String identifier, String slug, String category,
               String koreanName, String familyKey) {
            this.name = name;
            this.identifier = identifier;
            this.slug = slug;
            this.category = category;
            this.koreanName = koreanName;
            this.familyKey = familyKey;
        }
    }

    // ===================== 진입점 =====================

    public static void main(String[] args) throws IOException {
        System.out.println("[WhatDevice] 빌드 시작");

        // 1) 데이터 로드
        Map<String, String> merged = DeviceCatalog.loadMergedData();
        System.out.println("  - 병합된 전체 기기 수: " + merged.size());

        // 2) 범위 필터 + 슬러그/한글명/계열키 생성
        List<Device> devices = DeviceCatalog.buildDevices(merged);
        System.out.println("  - 범위(" + String.join(", ", CATEGORIES) + ") 내 기기 수: " + devices.size());

        // 3) 같은 계열끼리 묶기 (관련 기기 링크용)
        Map<String, List<Device>> families = groupByFamily(devices);

        // 3-1) 카테고리별로 묶기 (허브 페이지·홈 목록·푸터 내비게이션이 함께 쓴다)
        Map<String, List<Device>> byCategory = CategoryPages.groupByCategory(devices);

        // 4) dist 준비 + static 복사
        Files.createDirectories(DIST);
        copyStatic();

        // 5) 템플릿 로드
        String layout = readTemplate("layout.html");
        String deviceTpl = readTemplate("device.html");

        // 6) 기기 상세 페이지 생성
        generateDevicePages(devices, families, layout, deviceTpl);

        // 7) 검색용 데이터(JS) 생성
        writeDataJs(devices);

        // 8) 메인 페이지 생성
        generateIndex(devices, byCategory, layout);

        // 8-1) 카테고리 허브 페이지 생성 (크롤러가 기기 상세로 들어가는 유일한 경로)
        CategoryPages.generate(byCategory, layout);

        // 9) 가이드 글 + 정책 페이지 + 404 생성 (content/ 의 본문을 layout 으로 감쌈)
        ContentPages.generate(layout);
        ContentPages.generateNotFound(layout);

        // 10) 크롤러·AI 용 파일 (sitemap, robots, llms.txt, llms-full.txt, devices.csv)
        SeoFiles.writeAll(devices, byCategory);

        System.out.println("[WhatDevice] 빌드 완료 → dist/ (" + devices.size() + "개 기기 페이지)");
    }

    // ===================== 3. 계열 묶기 =====================

    /** familyKey 별로 기기를 묶는다(같은 계열 관련 기기 링크에 사용). */
    static Map<String, List<Device>> groupByFamily(List<Device> devices) {
        Map<String, List<Device>> map = new TreeMap<>();
        for (Device d : devices) {
            map.computeIfAbsent(d.familyKey, k -> new ArrayList<>()).add(d);
        }
        return map;
    }

    // ===================== 4. static 복사 =====================

    /** static/ 의 모든 파일을 dist/ 루트로 복사한다. */
    static void copyStatic() throws IOException {
        if (!Files.isDirectory(STATIC)) {
            return;
        }
        try (var stream = Files.walk(STATIC)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                Path rel = STATIC.relativize(src);
                Path dest = DIST.resolve(rel.toString());
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ===================== 5~6. 페이지 생성 =====================

    /** 기기 상세 페이지를 전부 생성한다. (dist/device/{슬러그}.html) */
    static void generateDevicePages(List<Device> devices, Map<String, List<Device>> families,
                                    String layout, String deviceTpl) throws IOException {
        Path dir = DIST.resolve("device");
        Files.createDirectories(dir);
        for (Device d : devices) {
            List<Device> family = families.get(d.familyKey);
            String related = buildRelatedList(d, family);
            List<String[]> faq = SeoMarkup.deviceFaq(d, family, "../");
            String categoryUrl = SITE_URL + "/category/" + CategoryPages.slugOf(d.category) + ".html";
            String canonical = SITE_URL + "/device/" + d.slug + ".html";
            String main = deviceTpl
                    .replace("{{BASE}}", "../")
                    .replace("{{IDENTIFIER}}", esc(d.identifier))
                    .replace("{{NAME}}", esc(d.name))
                    .replace("{{KOREAN}}", esc(d.koreanName))
                    .replace("{{CATEGORY}}", esc(d.category))
                    .replace("{{SLUG}}", esc(d.slug))
                    .replace("{{FAMILY}}", esc(d.familyKey) + ",x")
                    .replace("{{RELATED}}", related)
                    .replace("{{MODEL_DETAILS}}", readDeviceDetails(d.identifier))
                    .replace("{{CATEGORY_HREF}}", esc(CategoryPages.href("../", d.category)))
                    .replace("{{DEFINITION}}", SeoMarkup.definitionHtml(d))
                    .replace("{{FAQ}}", SeoMarkup.faqHtml(faq))
                    // 기기 상세는 고유 본문이 짧아(식별자·기종명·계열) 광고 비중이 커지면 색인에 불리하다.
                    // 상단은 비우고 애드핏 2 + 쿠팡 1 로 제한한다. 색인이 확인되면 그때 늘린다.
                    .replace("{{AD_TOP}}", "")
                    .replace("{{AD_MID}}", Ads.adSlotHtml(1))      // 같은 계열 목록 아래
                    .replace("{{AD_BOTTOM}}", Ads.coupangHtml())   // FAQ 아래
                    .replace("{{AD_END}}", Ads.adSlotHtml(2))      // 뒤로가기 링크 아래
                    + SeoMarkup.script(
                            SeoMarkup.breadcrumb(new String[]{"홈", SITE_URL + "/"},
                                    new String[]{d.category, categoryUrl},
                                    new String[]{d.identifier, canonical}),
                            SeoMarkup.faqPage(faq));

            String html = renderLayout(layout, "../", SeoMarkup.title(d),
                    SeoMarkup.description(d, family == null ? 1 : family.size()), canonical, main, "");
            Files.write(dir.resolve(d.slug + ".html"), html.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 같은 계열의 다른 기기들을 <li> 링크 목록으로 만든다(자기 자신 제외). */
    static String buildRelatedList(Device self, List<Device> family) {
        StringBuilder sb = new StringBuilder();
        if (family != null) {
            for (Device d : family) {
                if (d.identifier.equals(self.identifier)) {
                    continue;
                }
                // device/ 내부 형제 링크라 BASE 불필요(슬러그.html)
                sb.append("<li><a href=\"").append(d.slug).append(".html\">")
                  .append("<code class=\"mono\">").append(esc(d.identifier)).append("</code> ")
                  .append(esc(d.name)).append("</a></li>\n");
            }
        }
        if (sb.length() == 0) {
            sb.append("<li class=\"muted\">같은 계열의 다른 기기가 없습니다.</li>");
        }
        return sb.toString();
    }

    // ===================== 7. data.js =====================

    /** 검색용 데이터(JS 전역 배열)를 생성한다. dist/data.js */
    static void writeDataJs(List<Device> devices) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("// 자동 생성 파일 — 직접 수정하지 말 것 (BuildSite.java 가 생성).\n");
        sb.append("window.DEVICES = [\n");
        for (Device d : devices) {
            sb.append("  {i:").append(js(d.identifier))
              .append(", n:").append(js(d.name))
              .append(", k:").append(js(d.koreanName))
              .append(", s:").append(js(d.slug))
              .append(", c:").append(js(d.category))
              .append("},\n");
        }
        sb.append("];\n");
        Files.write(DIST.resolve("data.js"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ===================== 8. index =====================

    /** 메인(검색) 페이지 생성. */
    static void generateIndex(List<Device> devices, Map<String, List<Device>> byCategory,
                              String layout) throws IOException {
        // SPEC-DATA-001: 5개 카테고리 카운트 표시.
        long iphone = devices.stream().filter(d -> d.category.equals("iPhone")).count();
        long ipad = devices.stream().filter(d -> d.category.equals("iPad")).count();
        long watch = devices.stream().filter(d -> d.category.equals("Watch")).count();
        long ipod = devices.stream().filter(d -> d.category.equals("iPod")).count();
        long simulator = devices.stream().filter(d -> d.category.equals("Simulator")).count();
        long mac = devices.stream().filter(d -> d.category.equals("Mac")).count();
        long total = iphone + ipad + mac + watch + ipod + simulator;

        String main =
            "<section class=\"hero\">\n" +
            "  <h1>애플 기기 식별자 검색</h1>\n" +
            "  <p class=\"lead\">" + esc(SITE_DESC) + "</p>\n" +
            "  <p class=\"hint\">예: <code class=\"mono\">iPhone15,2</code> 또는 <code class=\"mono\">iPhone 14 Pro</code>, <code class=\"mono\">아이폰 14 프로</code></p>\n" +
            "  <div class=\"search-box\">\n" +
            "    <input id=\"q\" type=\"search\" autocomplete=\"off\" placeholder=\"식별자 또는 기종명으로 검색…\" aria-label=\"기기 검색\">\n" +
            "  </div>\n" +
            "  <p class=\"count muted\">현재 " + total + "개 기기 수록 (iPhone " + iphone + " · iPad " + ipad + " · Mac " + mac + " · Watch " + watch + " · iPod " + ipod + " · Simulator " + simulator + ")</p>\n" +
            "</section>\n" +
            Ads.adSlotHtml(0) +
            "<section id=\"results\" class=\"results\" aria-live=\"polite\"></section>\n" +
            Ads.coupangHtml() +
            // 검색 결과는 JS 가 그리므로 크롤러에게는 빈 영역이다. 크롤 경로 확보를 위해
            // 카테고리 허브와 기기 링크를 정적 HTML 로 함께 내보낸다.
            CategoryPages.homeSections(byCategory, readContent("home-intro.html")) +
            Ads.adSlotHtml(2) +
            SeoMarkup.script(SeoMarkup.webSite());

        String title = "아이폰·아이패드 기기 식별자 검색 | " + SITE_NAME;
        String desc = "iPhone15,2 같은 애플 기기 식별자(모델 코드)를 기종명으로, 또 그 반대로 즉시 변환·검색합니다.";
        String canonical = SITE_URL + "/";
        String scripts = "<script src=\"data.js\"></script>\n<script src=\"search.js\"></script>";

        String html = renderLayout(layout, "", title, desc, canonical, main, scripts);
        Files.write(DIST.resolve("index.html"), html.getBytes(StandardCharsets.UTF_8));
    }

    // ===================== 공통 유틸 =====================

    /** layout 템플릿에 공통 자리표시자를 채워 최종 HTML 을 만든다. */
    static String renderLayout(String layout, String base, String title, String description,
                               String canonical, String main, String scripts) {
        return layout
                .replace("{{BASE}}", base)
                .replace("{{TITLE}}", esc(title))
                .replace("{{DESCRIPTION}}", esc(description))
                .replace("{{CANONICAL}}", esc(canonical))
                .replace("{{DISCLAIMER}}", esc(DISCLAIMER))
                .replace("{{SITE_VERIFICATION}}", SeoMarkup.verificationMetas())
                .replace("{{CATEGORY_NAV}}", CategoryPages.footerNav(base))
                .replace("{{SCRIPTS}}", scripts)
                .replace("{{MAIN}}", main); // MAIN 은 이미 HTML 이므로 esc 하지 않음
    }

    static String readTemplate(String name) throws IOException {
        return new String(Files.readAllBytes(TEMPLATES.resolve(name)), StandardCharsets.UTF_8);
    }

    static String readContent(String name) throws IOException {
        return new String(Files.readAllBytes(CONTENT.resolve(name)), StandardCharsets.UTF_8);
    }

    static String readDeviceDetails(String identifier) throws IOException {
        String name = switch (identifier) {
            case "iPhone15,2" -> "device-iphone-14-pro.html";
            case "iPhone19,2" -> "device-iphone-18-pro.html";
            case "iPhone19,3", "iPhone19,7" -> "device-iphone-18-pro-max.html";
            case "iPhone19,4" -> "device-iphone-duo.html";
            default -> null;
        };
        return name == null ? "" : readContent(name);
    }

    /** HTML 텍스트 이스케이프 (속성/본문 텍스트용). */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** 문자열을 JS 리터럴(쌍따옴표)로 안전하게 직렬화. */
    static String js(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '<': sb.append("\\u003c"); break; // </script> 방지
                case '>': sb.append("\\u003e"); break;
                default: sb.append(c);
            }
        }
        return sb.append("\"").toString();
    }
}
