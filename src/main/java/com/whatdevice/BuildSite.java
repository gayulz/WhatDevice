package com.whatdevice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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
 * <p>data/ 의 JSON 을 읽어 병합·필터·가공한 뒤 templates/ 와 static/ 을 이용해
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
    static final String SITE_URL = "https://gayulz.github.io/WhatDevice";
    /** 문의 이메일 (지시서 2번 항목 5번 확정값). */
    static final String CONTACT_EMAIL = "gayulz@kakao.com";
    /** 면책 문구 (누락 금지). */
    static final String DISCLAIMER = "비공식 참고용 데이터입니다. 출처: clo4/apple_device_identifiers";

    /** 카카오 애드핏 PC 광고 단위 (728x90 leaderboard). */
    static final String ADFIT_UNIT_PC = "DAN-iE1zbQKstH7OpKgG";
    /** 카카오 애드핏 모바일 광고 단위 (320x100). */
    static final String ADFIT_UNIT_MOBILE = "DAN-Vx7eTegLs1VGrrwp";

    /**
     * 광고 슬롯 마크업을 만든다. PC/모바일 단위를 둘 다 박고 CSS 미디어 쿼리로 토글한다.
     * 카카오 SDK(layout.html 의 ba.min.js)가 페이지 내 모든 .kakao_ad_area 를 자동 스캔한다.
     */
    static String adSlotHtml() {
        return ""
            + "<div class=\"ad-slot\" aria-hidden=\"true\">\n"
            + "  <ins class=\"kakao_ad_area ad-pc\" style=\"display:none;\"\n"
            + "       data-ad-unit=\"" + ADFIT_UNIT_PC + "\"\n"
            + "       data-ad-width=\"728\" data-ad-height=\"90\"></ins>\n"
            + "  <ins class=\"kakao_ad_area ad-mobile\" style=\"display:none;\"\n"
            + "       data-ad-unit=\"" + ADFIT_UNIT_MOBILE + "\"\n"
            + "       data-ad-width=\"320\" data-ad-height=\"100\"></ins>\n"
            + "</div>\n";
    }

    /**
     * 사이트에 포함할 기기 카테고리(=식별자 접두어). 여기에 "iPod","Watch","Mac" 등을
     * 추가하면 범위가 바로 넓어진다. (지시서: 카테고리 토글로 확장 가능하게 설계)
     */
    static final List<String> CATEGORIES = List.of("iPhone", "iPad");

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

        ObjectMapper mapper = new ObjectMapper();

        // 1) 원본 + override 병합
        Map<String, String> merged = loadMergedData(mapper);
        System.out.println("  - 병합된 전체 기기 수: " + merged.size());

        // 2) 범위 필터 + 슬러그/한글명/계열키 생성
        List<Device> devices = buildDevices(merged);
        System.out.println("  - 범위(" + String.join(", ", CATEGORIES) + ") 내 기기 수: " + devices.size());

        // 3) 같은 계열끼리 묶기 (관련 기기 링크용)
        Map<String, List<Device>> families = groupByFamily(devices);

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
        generateIndex(devices, layout);

        // 9) 가이드 글 + 정책 페이지 생성 (content/ 의 본문을 layout 으로 감쌈)
        generateContentPages(layout);

        // 10) SEO 파일
        writeSitemap(devices);
        writeRobots();

        System.out.println("[WhatDevice] 빌드 완료 → dist/ (" + devices.size() + "개 기기 페이지)");
    }

    // ===================== 1. 데이터 로드/병합 =====================

    /**
     * devices.json(원본)과 overrides.json(보강)을 읽어 하나의 맵으로 병합한다.
     * override 값이 원본보다 우선한다. "_" 로 시작하는 키(주석용)는 무시한다.
     */
    static Map<String, String> loadMergedData(ObjectMapper mapper) throws IOException {
        Map<String, String> merged = new LinkedHashMap<>();
        readJsonStringMap(mapper, DATA.resolve("devices.json"), merged);
        readJsonStringMap(mapper, DATA.resolve("overrides.json"), merged); // 나중에 넣어 override 우선
        return merged;
    }

    /** JSON 객체({"이름":"식별자"})를 읽어 target 맵에 채운다. 값이 문자열인 항목만 취한다. */
    static void readJsonStringMap(ObjectMapper mapper, Path file, Map<String, String> target)
            throws IOException {
        if (!Files.exists(file)) {
            throw new IOException("필수 데이터 파일 없음: " + file.toAbsolutePath());
        }
        JsonNode root = mapper.readTree(Files.readAllBytes(file));
        Iterator<Map.Entry<String, JsonNode>> it = root.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            if (key.startsWith("_")) {
                continue; // 주석용 키 (예: "_comment")
            }
            if (e.getValue().isTextual()) {
                target.put(key, e.getValue().asText());
            }
        }
    }

    // ===================== 2. 기기 목록 가공 =====================

    /** 병합 데이터에서 범위에 맞는 기기만 골라 Device 리스트를 만든다(정렬 포함). */
    static List<Device> buildDevices(Map<String, String> merged) {
        List<Device> list = new ArrayList<>();
        for (Map.Entry<String, String> e : merged.entrySet()) {
            String name = e.getKey();
            String identifier = e.getValue();
            if (!inScope(identifier)) {
                continue;
            }
            String category = categoryOf(identifier);
            String slug = toSlug(identifier);
            String korean = toKorean(name);
            String family = familyKeyOf(identifier);
            list.add(new Device(name, identifier, slug, category, korean, family));
        }
        // 카테고리 → 계열 major → minor 순으로 내림차순(최신 기기가 위로).
        list.sort(comparator());
        return list;
    }

    /** 식별자가 우리가 다루는 카테고리(접두어)에 속하는지. */
    static boolean inScope(String identifier) {
        return categoryOf(identifier) != null;
    }

    /** 식별자의 카테고리(접두어)를 돌려준다. 범위 밖이면 null. */
    static String categoryOf(String identifier) {
        for (String c : CATEGORIES) {
            if (identifier.startsWith(c)) {
                return c;
            }
        }
        return null;
    }

    /** 식별자 → 슬러그. 소문자화 후 영숫자 외 문자를 하이픈으로, 중복/양끝 하이픈 정리. */
    static String toSlug(String identifier) {
        String s = identifier.toLowerCase();
        s = s.replaceAll("[^a-z0-9]+", "-");
        s = s.replaceAll("(^-+)|(-+$)", "");
        return s;
    }

    /** "iPhone15,2" → 계열키 "iPhone15" (콤마 앞부분). */
    static String familyKeyOf(String identifier) {
        int comma = identifier.indexOf(',');
        return comma >= 0 ? identifier.substring(0, comma) : identifier;
    }

    // 영문 토큰 → 한글 표기 (검색·참고용 자동 생성. 완벽 번역이 아니라 best-effort)
    private static final String[][] KO_TOKENS = {
            {"iPhone", "아이폰"},
            {"iPad", "아이패드"},
            {"Pro Max", "프로 맥스"},
            {"Pro", "프로"},
            {"Plus", "플러스"},
            {"Max", "맥스"},
            {"mini", "미니"},
            {"Mini", "미니"},
            {"Air", "에어"},
            {"Ultra", "울트라"},
            {"inch", "인치"},
            {"generation", "세대"},
            {"Wi-Fi", "와이파이"},
            {"Cellular", "셀룰러"},
    };

    /** 기종명을 한글 표기로 치환(부분 일치 토큰 교체). 숫자/괄호 등은 그대로 둔다. */
    static String toKorean(String name) {
        String s = name;
        for (String[] t : KO_TOKENS) {
            s = s.replace(t[0], t[1]);
        }
        return s;
    }

    /** 정렬 비교자: 카테고리 오름차순, 그 안에서 계열 major·minor 내림차순(최신 우선). */
    static Comparator<Device> comparator() {
        return (a, b) -> {
            int c = a.category.compareTo(b.category);
            if (c != 0) {
                return c;
            }
            int[] na = majorMinor(a.identifier);
            int[] nb = majorMinor(b.identifier);
            if (na[0] != nb[0]) {
                return Integer.compare(nb[0], na[0]); // major 내림차순
            }
            if (na[1] != nb[1]) {
                return Integer.compare(nb[1], na[1]); // minor 내림차순
            }
            return a.name.compareTo(b.name);
        };
    }

    private static final Pattern MAJOR_MINOR = Pattern.compile("(\\d+),(\\d+)");

    /** "iPhone15,2" → [15, 2]. 못 찾으면 [0,0]. */
    static int[] majorMinor(String identifier) {
        Matcher m = MAJOR_MINOR.matcher(identifier);
        if (m.find()) {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))};
        }
        return new int[]{0, 0};
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
            String related = buildRelatedList(d, families.get(d.familyKey));
            String main = deviceTpl
                    .replace("{{BASE}}", "../")
                    .replace("{{IDENTIFIER}}", esc(d.identifier))
                    .replace("{{NAME}}", esc(d.name))
                    .replace("{{KOREAN}}", esc(d.koreanName))
                    .replace("{{CATEGORY}}", esc(d.category))
                    .replace("{{SLUG}}", esc(d.slug))
                    .replace("{{FAMILY}}", esc(d.familyKey) + ",x")
                    .replace("{{RELATED}}", related);

            String title = d.identifier + " - " + d.name + " | " + SITE_NAME;
            String desc = d.identifier + "는 " + d.name + "의 애플 모델 식별자입니다. 식별자와 기종명을 양방향으로 확인하세요.";
            String canonical = SITE_URL + "/device/" + d.slug + ".html";

            String html = renderLayout(layout, "../", title, desc, canonical, main, "");
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
    static void generateIndex(List<Device> devices, String layout) throws IOException {
        long iphone = devices.stream().filter(d -> d.category.equals("iPhone")).count();
        long ipad = devices.stream().filter(d -> d.category.equals("iPad")).count();

        String main =
            "<section class=\"hero\">\n" +
            "  <h1>" + esc(SITE_NAME) + "</h1>\n" +
            "  <p class=\"lead\">" + esc(SITE_DESC) + "</p>\n" +
            "  <p class=\"hint\">예: <code class=\"mono\">iPhone15,2</code> 또는 <code class=\"mono\">iPhone 14 Pro</code>, <code class=\"mono\">아이폰 14 프로</code></p>\n" +
            "  <div class=\"search-box\">\n" +
            "    <input id=\"q\" type=\"search\" autocomplete=\"off\" placeholder=\"식별자 또는 기종명으로 검색…\" aria-label=\"기기 검색\">\n" +
            "  </div>\n" +
            "  <p class=\"count muted\">현재 " + (iphone + ipad) + "개 기기 수록 (iPhone " + iphone + " · iPad " + ipad + ")</p>\n" +
            "</section>\n" +
            adSlotHtml() +
            "<section id=\"results\" class=\"results\" aria-live=\"polite\"></section>\n";

        String title = SITE_NAME + " — 애플 기기 식별자 변환기";
        String desc = "iPhone15,2 같은 애플 기기 식별자(모델 코드)를 기종명으로, 또 그 반대로 즉시 변환·검색합니다.";
        String canonical = SITE_URL + "/";
        String scripts = "<script src=\"data.js\"></script>\n<script src=\"search.js\"></script>";

        String html = renderLayout(layout, "", title, desc, canonical, main, scripts);
        Files.write(DIST.resolve("index.html"), html.getBytes(StandardCharsets.UTF_8));
    }

    // ===================== 9. 가이드/정책 페이지 =====================

    /**
     * content/ 디렉토리의 본문 HTML 조각들을 layout 으로 감싸 정적 페이지로 만든다.
     * 각 항목: {출력경로, 제목, 설명, 본문파일}.
     */
    static void generateContentPages(String layout) throws IOException {
        // {출력 상대경로, BASE, 제목, 설명, content 파일명}
        String[][] pages = {
            {"guide/identifier-basics.html", "../", "애플 기기 식별자(모델 코드)란 무엇인가",
                    "iPhone15,2 같은 애플 기기 식별자가 무엇이고 어디에 쓰이는지 쉽게 설명합니다.",
                    "guide-identifier-basics.html"},
            {"guide/find-from-logs.html", "../", "크래시 로그·애널리틱스에서 기기 모델 확인하는 법",
                    "크래시 로그나 애널리틱스에 찍힌 기기 식별자로 실제 기종을 알아내는 방법을 안내합니다.",
                    "guide-find-from-logs.html"},
            {"policy/terms.html", "../", "이용약관",
                    SITE_NAME + " 이용약관입니다.", "policy-terms.html"},
            {"policy/privacy.html", "../", "개인정보처리방침",
                    SITE_NAME + " 개인정보처리방침입니다.", "policy-privacy.html"},
            {"policy/contact.html", "../", "문의",
                    SITE_NAME + " 문의 안내입니다.", "policy-contact.html"},
        };

        for (String[] p : pages) {
            String body = readContent(p[4]);
            // 본문 안의 토큰 치환(이메일/사이트명/BASE)
            body = body
                    .replace("{{BASE}}", p[1])
                    .replace("{{EMAIL}}", CONTACT_EMAIL)
                    .replace("{{SITE_NAME}}", SITE_NAME);
            // 가이드 페이지에만 본문 끝에 광고 슬롯 추가 (정책 페이지는 광고 미노출 — 신뢰도 보호)
            if (p[0].startsWith("guide/")) {
                body = body + "\n" + adSlotHtml();
            }
            String title = p[2] + " | " + SITE_NAME;
            String canonical = SITE_URL + "/" + p[0];
            String html = renderLayout(layout, p[1], title, p[3], canonical, body, "");
            Path out = DIST.resolve(p[0]);
            Files.createDirectories(out.getParent());
            Files.write(out, html.getBytes(StandardCharsets.UTF_8));
        }
    }

    // ===================== 10. SEO 파일 =====================

    /** 전 페이지 URL 을 담은 sitemap.xml 생성. */
    static void writeSitemap(List<Device> devices) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        // 고정 페이지
        appendUrl(sb, SITE_URL + "/");
        appendUrl(sb, SITE_URL + "/guide/identifier-basics.html");
        appendUrl(sb, SITE_URL + "/guide/find-from-logs.html");
        appendUrl(sb, SITE_URL + "/policy/terms.html");
        appendUrl(sb, SITE_URL + "/policy/privacy.html");
        appendUrl(sb, SITE_URL + "/policy/contact.html");
        // 기기 페이지
        for (Device d : devices) {
            appendUrl(sb, SITE_URL + "/device/" + d.slug + ".html");
        }
        sb.append("</urlset>\n");
        Files.write(DIST.resolve("sitemap.xml"), sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static void appendUrl(StringBuilder sb, String loc) {
        sb.append("  <url><loc>").append(esc(loc)).append("</loc></url>\n");
    }

    /** robots.txt 생성 (sitemap 위치 명시). */
    static void writeRobots() throws IOException {
        String txt = "User-agent: *\n"
                + "Allow: /\n\n"
                + "Sitemap: " + SITE_URL + "/sitemap.xml\n";
        Files.write(DIST.resolve("robots.txt"), txt.getBytes(StandardCharsets.UTF_8));
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
                .replace("{{SCRIPTS}}", scripts)
                .replace("{{MAIN}}", main); // MAIN 은 이미 HTML 이므로 esc 하지 않음
    }

    static String readTemplate(String name) throws IOException {
        return new String(Files.readAllBytes(TEMPLATES.resolve(name)), StandardCharsets.UTF_8);
    }

    static String readContent(String name) throws IOException {
        return new String(Files.readAllBytes(CONTENT.resolve(name)), StandardCharsets.UTF_8);
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
