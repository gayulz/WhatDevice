package com.whatdevice;

import java.util.List;
import java.util.Map;

public class DeviceDataTest {
    public static void main(String[] args) throws Exception {
        Map<String, String> expected = Map.ofEntries(
                Map.entry("iPhone17,5", "iPhone 16e"),
                Map.entry("iPhone18,1", "iPhone 17 Pro"),
                Map.entry("iPhone18,2", "iPhone 17 Pro Max"),
                Map.entry("iPhone18,3", "iPhone 17"),
                Map.entry("iPhone18,4", "iPhone Air"),
                Map.entry("iPhone18,5", "iPhone 17e"),
                Map.entry("iPhone19,2", "iPhone 18 Pro"),
                Map.entry("iPhone19,3", "iPhone 18 Pro Max"),
                Map.entry("iPhone19,4", "iPhone Duo"),
                Map.entry("iPhone19,7", "iPhone 18 Pro Max"));
        Map<String, String> data = DeviceCatalog.loadMergedData();
        for (var entry : expected.entrySet()) {
            assert entry.getValue().equals(data.get(entry.getKey())) : entry.getKey();
        }

        List<BuildSite.Device> devices = DeviceCatalog.buildDevices(data);
        for (String identifier : expected.keySet()) {
            assert devices.stream().filter(d -> d.identifier.equals(identifier)).count() == 1
                    : identifier;
        }
        assert devices.stream().filter(d -> d.name.equals("iPhone 18 Pro Max")).count() == 2;
        assert DeviceCatalog.toKorean("iPhone Duo").equals("아이폰 듀오");
        assert DeviceCatalog.toKorean("iPhone Air").equals("아이폰 에어");
        assert DeviceCatalog.toKorean("iPhone 18 Pro Max").equals("아이폰 18 프로 맥스");
        for (String identifier : List.of("i386", "x86_64", "arm64")) {
            assert "iPhone Simulator".equals(data.get(identifier)) : identifier;
        }
        assert BuildSite.readDeviceDetails("iPhone15,2").contains("A16 Bionic");
        assert BuildSite.readDeviceDetails("iPhone19,2").contains("A20 Pro");
        assert BuildSite.readDeviceDetails("iPhone19,3").contains("글로벌형");
        assert BuildSite.readDeviceDetails("iPhone19,3").equals(BuildSite.readDeviceDetails("iPhone19,7"));
        assert BuildSite.readDeviceDetails("iPhone19,4").contains("폴딩 디스플레이");
        assert BuildSite.readDeviceDetails("i386").isEmpty();
        String introduction = BuildSite.readContent("home-intro.html");
        String directory = CategoryPages.homeSections(CategoryPages.groupByCategory(devices), introduction);
        assert directory.indexOf("id=\"directory\"") < directory.indexOf("id=\"lookup-guide\"");
        assert directory.indexOf("id=\"lookup-guide\"") < directory.indexOf("class=\"home-category\"");
        // Mac: Apple 지원 문서 기준 보강. 두 식별자 체계(MacBookPro18,3 · Mac14,2)와 iMac 접두어가 모두 Mac 으로 묶인다.
        assert "MacBook Pro (14-inch, 2021)".equals(data.get("MacBookPro18,3"));
        assert "MacBook Air (M2, 2022)".equals(data.get("Mac14,2"));
        assert data.get("MacBookPro15,1").contains(" / ") : "여러 판매 모델이 쓰는 식별자는 이름을 잇는다";
        for (String identifier : List.of("MacBookPro18,3", "Mac14,2", "iMac21,1", "Macmini9,1", "iMacPro1,1")) {
            assert "Mac".equals(DeviceCatalog.categoryOf(identifier)) : identifier;
        }
        assert DeviceCatalog.toKorean("MacBook Pro (14-inch, 2021)").equals("맥북 프로 (14-인치, 2021)");
        assert DeviceCatalog.toKorean("iMac (24-inch, M1, 2021)").startsWith("아이맥 ");
        assert DeviceCatalog.toKorean("Mac Studio (2022)").equals("맥 스튜디오 (2022)");
        assert DeviceCatalog.toKorean("Mac mini (M1, 2020)").equals("맥 미니 (M1, 2020)");
        System.out.println("최신 iPhone 식별자 10개·지역별 중복 모델·한글 표기 검증 통과");

        verifyAds(devices);
        verifySeoMarkup(devices);
        verifySeoFiles(devices);
    }

    /** 광고 마크업: 발급된 슬롯은 PC·모바일 쌍으로 나오고, 없는 슬롯은 빈 문자열이다. */
    private static void verifyAds(List<BuildSite.Device> devices) {
        for (int slot = 0; slot < Ads.AD_SLOTS.length; slot++) {
            assert count(Ads.adSlotHtml(slot), "class=\"kakao_ad_area") == 2 : "slot " + slot;
        }
        assert Ads.adSlotHtml(Ads.AD_SLOTS.length).isEmpty();
        // 허브는 계열별 소제목으로 나뉘고, 긴 목록에만 광고 1개가 들어간다.
        List<BuildSite.Device> longList = devices.subList(0, CategoryPages.MID_AD_MIN_ITEMS);
        String split = CategoryPages.familySections(longList);
        assert count(split, "kakao_ad_area") == 2 : "긴 허브 목록은 광고 1개(PC·모바일 쌍)";
        assert count(split, "<li>") == longList.size() : "계열로 나눠도 링크 수 유지";
        assert count(split, "<h2>") == longList.stream().map(d -> d.familyKey).distinct().count();
        String whole = CategoryPages.familySections(devices.subList(0, CategoryPages.MID_AD_MIN_ITEMS - 1));
        assert count(whole, "kakao_ad_area") == 0 : "짧은 허브 목록은 광고 없음";
        String iphone = CategoryPages.familySections(devices.stream().filter(d -> d.category.equals("iPhone")).toList());
        assert iphone.contains("<h2>iPhone15,x 계열</h2>") && iphone.contains("iPhone 14 Pro") : "계열 소제목";
        System.out.println("광고 슬롯·허브 목록 분할 검증 통과");
    }

    /** JSON-LD·검색용 문구: 화면 FAQ 와 FAQPage 가 같은 문구에서 나오고, 스크립트 종료 태그가 새지 않는다. */
    private static void verifySeoMarkup(List<BuildSite.Device> devices) {
        BuildSite.Device pro = devices.stream().filter(d -> d.identifier.equals("iPhone15,2")).findFirst().get();
        List<BuildSite.Device> family = devices.stream().filter(d -> d.familyKey.equals(pro.familyKey)).toList();
        List<String[]> faq = SeoMarkup.deviceFaq(pro, family, "../");
        assert faq.size() == 3 : "계열에 형제가 있으면 FAQ 3문항";
        assert SeoMarkup.deviceFaq(pro, List.of(pro), "../").size() == 2 : "형제가 없으면 FAQ 2문항";

        String json = SeoMarkup.faqPage(faq);
        assert json.contains("아이폰 14 프로") && json.contains("FAQPage") : json;
        assert !json.contains("<") && !json.contains(">") : "JSON-LD 텍스트에 태그가 남음";
        assert SeoMarkup.faqHtml(faq).contains("<code class=\"mono\">iPhone15,2</code>");

        String script = SeoMarkup.script(SeoMarkup.webSite(), SeoMarkup.breadcrumb(
                new String[]{"홈", BuildSite.SITE_URL + "/"}, new String[]{"iPhone", BuildSite.SITE_URL + "/category/iphone.html"}));
        assert script.startsWith("<script type=\"application/ld+json\">") && script.endsWith("</script>\n");
        assert count(script, "\"position\"") == 2 && count(script, "</script>") == 1 : script;

        BuildSite.Device air = devices.stream().filter(d -> d.identifier.equals("Mac14,2")).findFirst().get();
        String macFaq = SeoMarkup.deviceFaq(air, List.of(air), "../").get(1)[1];
        assert macFaq.contains("hw.model") && !macFaq.contains("hw.machine") : "Mac 은 hw.model 로 안내";
        assert SeoMarkup.title(pro).contains("아이폰 14 프로") && SeoMarkup.title(pro).contains("iPhone15,2");
        assert SeoMarkup.description(pro, family.size()).contains("iPhone15,x 계열 " + family.size() + "종");
        assert !SeoMarkup.description(pro, 1).contains("계열");
        BuildSite.Device simulator = devices.stream().filter(d -> d.identifier.equals("arm64")).findFirst().get();
        assert !SeoMarkup.description(simulator, 1).contains("null");
        assert !SeoMarkup.verificationMetas().contains("content=\"\"") : "빈 소유확인 코드로 메타가 나옴";
        System.out.println("JSON-LD·기기 상세 문구 검증 통과");
    }

    /** llms·CSV·사이트맵: 기기 수만큼 행이 있고, 콤마가 든 식별자가 CSV 칸을 깨지 않는다. */
    private static void verifySeoFiles(List<BuildSite.Device> devices) throws Exception {
        var byCategory = CategoryPages.groupByCategory(devices);
        String csv = SeoFiles.csv(devices);
        assert csv.lines().count() == devices.size() + 1 : "CSV 헤더 + 기기 수";
        assert csv.contains("\"iPhone15,2\",\"iPhone 14 Pro\",\"아이폰 14 프로\",\"iPhone\"") : "CSV 인용";
        String full = SeoFiles.llmsFull(byCategory);
        assert count(full, "\n- ") == devices.size() : "llms-full 에 기기 전부";
        assert full.contains("- iPhone15,2 — iPhone 14 Pro (아이폰 14 프로)");
        String llms = SeoFiles.llms(byCategory);
        assert llms.startsWith("# WhatDevice\n\n> ") && llms.contains("llms-full.txt") && llms.contains("iphone-model-check");
        String sitemap = SeoFiles.sitemap(devices, byCategory);
        assert count(sitemap, "<loc>") == 1 + ContentPages.PAGES.length + byCategory.size() + devices.size();
        for (String[] p : ContentPages.PAGES) {
            assert java.nio.file.Files.exists(BuildSite.CONTENT.resolve(p[3])) : p[3];
        }
        for (String category : byCategory.keySet()) {
            assert !CategoryPages.introduction(category).isEmpty() : "허브 소개문 없음: " + category;
        }
        System.out.println("llms·CSV·사이트맵·허브 소개문 검증 통과");
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
