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
        Map<String, String> data = BuildSite.loadMergedData();
        for (var entry : expected.entrySet()) {
            assert entry.getValue().equals(data.get(entry.getKey())) : entry.getKey();
        }

        List<BuildSite.Device> devices = BuildSite.buildDevices(data);
        for (String identifier : expected.keySet()) {
            assert devices.stream().filter(d -> d.identifier.equals(identifier)).count() == 1
                    : identifier;
        }
        assert devices.stream().filter(d -> d.name.equals("iPhone 18 Pro Max")).count() == 2;
        assert BuildSite.toKorean("iPhone Duo").equals("아이폰 듀오");
        assert BuildSite.toKorean("iPhone Air").equals("아이폰 에어");
        assert BuildSite.toKorean("iPhone 18 Pro Max").equals("아이폰 18 프로 맥스");
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
        System.out.println("최신 iPhone 식별자 10개·지역별 중복 모델·한글 표기 검증 통과");

        verifyAds(devices);
        verifySeoMarkup(devices);
    }

    /** 광고 마크업: 발급된 슬롯은 PC·모바일 쌍으로 나오고, 없는 슬롯은 빈 문자열이다. */
    private static void verifyAds(List<BuildSite.Device> devices) {
        for (int slot = 0; slot < Ads.AD_SLOTS.length; slot++) {
            assert count(Ads.adSlotHtml(slot), "class=\"kakao_ad_area") == 2 : "slot " + slot;
        }
        assert Ads.adSlotHtml(Ads.AD_SLOTS.length).isEmpty();
        // 긴 목록은 가운데에서 끊어 광고 1개, 짧은 목록은 광고 없이 하나의 ul
        List<BuildSite.Device> longList = devices.subList(0, CategoryPages.MID_AD_MIN_ITEMS);
        String split = CategoryPages.deviceListWithMidAd(longList);
        assert count(split, "<ul ") == 2 && count(split, "kakao_ad_area") == 2 : "긴 허브 목록은 광고로 분할";
        assert count(split, "<li>") == longList.size() : "분할해도 링크 수 유지";
        String whole = CategoryPages.deviceListWithMidAd(devices.subList(0, CategoryPages.MID_AD_MIN_ITEMS - 1));
        assert count(whole, "<ul ") == 1 && count(whole, "kakao_ad_area") == 0 : "짧은 허브 목록은 분할 안 함";
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

        assert SeoMarkup.title(pro).contains("아이폰 14 프로") && SeoMarkup.title(pro).contains("iPhone15,2");
        assert SeoMarkup.description(pro, family.size()).contains("iPhone15,x 계열 " + family.size() + "종");
        assert !SeoMarkup.description(pro, 1).contains("계열");
        BuildSite.Device simulator = devices.stream().filter(d -> d.identifier.equals("arm64")).findFirst().get();
        assert !SeoMarkup.description(simulator, 1).contains("null");
        assert !SeoMarkup.verificationMetas().contains("content=\"\"") : "빈 소유확인 코드로 메타가 나옴";
        System.out.println("JSON-LD·기기 상세 문구 검증 통과");
    }

    private static int count(String text, String needle) {
        return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }
}
