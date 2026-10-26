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
    }
}
