package com.whatdevice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Device data: loads the gist mirror plus the supplement, filters to the site's categories,
 * and derives slug, Korean name, family key and sort order. No HTML here.
 */
final class DeviceCatalog {

    private DeviceCatalog() {
    }

    // ===================== 1. 데이터 로드/병합 =====================

    /**
     * 기기 데이터를 읽어 Map&lt;식별자, 기종명&gt; 으로 돌려준다.
     *
     * <p>adamawolf/3048717 gist 미러(data/devices.txt)에 없는 식별자만
     * data/devices-supplement.txt에서 보강한다. 같은 식별자는 gist 값을 우선한다.
     * 형식은 각 라인 "{identifier} : {name}" — 콜론 양쪽에 공백이 있다.
     * 미러는 CI가 주 1회 자동 동기화하므로 보강 항목은 별도 파일에서 관리한다.
     */
    static Map<String, String> loadMergedData() throws IOException {
        Map<String, String> merged = loadData(BuildSite.DATA.resolve("devices.txt"));
        loadData(BuildSite.DATA.resolve("devices-supplement.txt")).forEach(merged::putIfAbsent);
        return merged;
    }

    static Map<String, String> loadData(Path file) throws IOException {
        Map<String, String> merged = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            throw new IOException("필수 데이터 파일 없음: " + file.toAbsolutePath());
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int idx = trimmed.indexOf(" : ");
            if (idx < 0) {
                continue; // 형식이 깨진 라인은 건너뜀
            }
            String identifier = trimmed.substring(0, idx).trim();
            String name = trimmed.substring(idx + 3).trim();
            if (identifier.isEmpty() || name.isEmpty()) {
                continue;
            }
            // 기존 호출자(buildDevices)는 entry.getKey()를 name, entry.getValue()를 identifier로 사용.
            // 동일 이름이 여러 식별자에 대응하므로(예: iPhone Simulator 3개) Map 키를 식별자로 두면 안 됨.
            // 키 충돌 방지를 위해 식별자를 키로 두는 대신 name+"\0"+identifier 같은 합성 키 사용 가능하지만,
            // buildDevices는 entrySet 순회만 하므로 LinkedHashMap에 동일 이름을 안전하게 적재하려면
            // 식별자를 키로 사용한다(고유). 대신 buildDevices에서 key=identifier, value=name 으로 해석하도록 수정.
            merged.put(identifier, name);
        }
        return merged;
    }


    // ===================== 2. 기기 목록 가공 =====================

    /**
     * 병합 데이터에서 범위에 맞는 기기만 골라 Device 리스트를 만든다(정렬 포함).
     * SPEC-DATA-001: gist 파서가 Map<identifier, name>을 반환하므로 키·값 해석을 식별자·이름으로 통일.
     */
    static List<BuildSite.Device> buildDevices(Map<String, String> merged) {
        List<BuildSite.Device> list = new ArrayList<>();
        for (Map.Entry<String, String> e : merged.entrySet()) {
            String identifier = e.getKey();
            String name = e.getValue();
            if (!inScope(identifier)) {
                continue;
            }
            String category = categoryOf(identifier);
            String slug = toSlug(identifier);
            String korean = toKorean(name);
            String family = familyKeyOf(identifier);
            list.add(new BuildSite.Device(name, identifier, slug, category, korean, family));
        }
        // 카테고리 → 계열 major → minor 순으로 내림차순(최신 기기가 위로).
        list.sort(comparator());
        return list;
    }

    /** 식별자가 우리가 다루는 카테고리(접두어)에 속하는지. */
    static boolean inScope(String identifier) {
        return categoryOf(identifier) != null;
    }

    /**
     * 식별자의 카테고리(접두어)를 돌려준다. 범위 밖이면 null.
     * SPEC-DATA-001: i386/x86_64/arm64 는 접두어 매칭 불가 → Simulator 카테고리로 명시 분기.
     */
    static String categoryOf(String identifier) {
        if (identifier.equals("i386") || identifier.equals("x86_64") || identifier.equals("arm64")) {
            return "Simulator";
        }
        // Mac 은 MacBookPro18,3 · Mac14,2 처럼 "Mac" 으로 시작하지만 iMac 계열만 접두어가 다르다.
        if (identifier.startsWith("iMac")) {
            return "Mac";
        }
        for (String c : BuildSite.CATEGORIES) {
            if (c.equals("Simulator")) {
                continue; // 위 명시 분기에서 처리됨
            }
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
            // SPEC-DATA-001: 긴 토큰 먼저 매칭 (Apple Watch가 Watch보다 우선, iPod touch가 iPod보다 우선).
            {"Apple Watch", "애플 워치"},
            {"iPod touch", "아이팟 터치"},
            {"iPhone Simulator", "아이폰 시뮬레이터"},
            {"Pro Max", "프로 맥스"},
            // Mac: MacBook·iMac 이 Mac 보다 먼저 와야 "맥Book" 같은 반쪽 치환이 생기지 않는다.
            {"MacBook", "맥북"},
            {"iMac", "아이맥"},
            {"Mac", "맥"},
            {"Studio", "스튜디오"},
            {"Retina", "레티나"},
            // 기본 카테고리
            {"iPhone", "아이폰"},
            {"iPad", "아이패드"},
            {"iPod", "아이팟"},
            {"Watch", "워치"},
            {"Simulator", "시뮬레이터"},
            // 모델 수식어
            {"Pro", "프로"},
            {"Plus", "플러스"},
            {"Max", "맥스"},
            {"mini", "미니"},
            {"Mini", "미니"},
            {"Air", "에어"},
            {"Duo", "듀오"},
            {"Ultra", "울트라"},
            {"Series", "시리즈"},
            // 일반 토큰
            {"inch", "인치"},
            {"generation", "세대"},
            {"Wi-Fi", "와이파이"},
            {"Cellular", "셀룰러"},
            {"case", "케이스"},
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
    static Comparator<BuildSite.Device> comparator() {
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
}
