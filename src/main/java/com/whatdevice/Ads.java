package com.whatdevice;

/**
 * 광고 슬롯 정의와 마크업 생성 전담. 페이지 유형별 배치는 호출하는 쪽(BuildSite, CategoryPages)이 정한다.
 *
 * <p>BuildSite 가 이미 길어(500줄 한도 초과) 광고 코드를 분리했다. 슬롯 ID 를 바꿀 때는 여기만 본다.
 */
final class Ads {

    private Ads() {
    }

    /**
     * 카카오 애드핏 광고 슬롯 정의. 한 페이지 안에서 같은 광고단위 ID를 두 번 쓰면
     * 애드핏 SDK가 하나만 렌더링하므로, 슬롯 위치마다 별도 광고단위를 발급받아 채운다.
     *
     * <p>각 행: {PC 단위ID, PC 가로, PC 세로, 모바일 단위ID, 모바일 가로, 모바일 세로}
     * ID가 비어 있는 슬롯은 마크업 자체를 만들지 않으므로 빈 채로 두어도 안전하다.
     */
    static final String[][] AD_SLOTS = {
            // 0번 — 본문 상단 (기존 발급분)
            {"DAN-iE1zbQKstH7OpKgG", "728", "90", "DAN-Vx7eTegLs1VGrrwp", "320", "100"},
            // 1번 — 본문 중단 (모바일 300x250 이라 긴 목록·본문의 중간에 둔다)
            {"DAN-WskrFomUCrWzoHhN", "728", "90", "DAN-cMiMxf4Cx55QKekZ", "300", "250"},
            // 2번 — 본문 하단
            {"DAN-PzvZoJzyVSkvsJ4v", "728", "90", "DAN-akCDuUdchdUs1Pmi", "320", "100"},
    };

    /**
     * 광고 슬롯 마크업을 만든다. PC/모바일 단위를 둘 다 박고 CSS 미디어 쿼리로 토글한다.
     * 카카오 SDK(layout.html 의 ba.min.js)가 페이지 내 모든 .kakao_ad_area 를 자동 스캔한다.
     *
     * @param slot AD_SLOTS 인덱스. 범위를 벗어나거나 ID가 비었으면 빈 문자열을 돌려준다.
     */
    static String adSlotHtml(int slot) {
        if (slot < 0 || slot >= AD_SLOTS.length) {
            return "";
        }
        String[] s = AD_SLOTS[slot];
        String pcUnit = s[0];
        String mobileUnit = s[3];
        if (pcUnit.isEmpty() && mobileUnit.isEmpty()) {
            return ""; // 미발급 슬롯 — 빈 박스가 생기지 않도록 마크업을 통째로 생략
        }
        StringBuilder sb = new StringBuilder();
        // CLS 방지용 예약 높이를 CSS 변수로 넘긴다(슬롯마다 크기가 달라서 인라인으로 지정).
        sb.append("<div class=\"ad-slot\" aria-hidden=\"true\" style=\"--ad-h-mobile:")
          .append(s[5]).append("px;--ad-h-pc:").append(s[2]).append("px;\">\n");
        if (!pcUnit.isEmpty()) {
            sb.append("  <ins class=\"kakao_ad_area ad-pc\" style=\"display:none;\"\n")
              .append("       data-ad-unit=\"").append(pcUnit).append("\"\n")
              .append("       data-ad-width=\"").append(s[1])
              .append("\" data-ad-height=\"").append(s[2]).append("\"></ins>\n");
        }
        if (!mobileUnit.isEmpty()) {
            sb.append("  <ins class=\"kakao_ad_area ad-mobile\" style=\"display:none;\"\n")
              .append("       data-ad-unit=\"").append(mobileUnit).append("\"\n")
              .append("       data-ad-width=\"").append(s[4])
              .append("\" data-ad-height=\"").append(s[5]).append("\"></ins>\n");
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    /** 쿠팡 파트너스 배너(고객 관심 기반 추천, carousel) 발급 정보. */
    static final String COUPANG_ID = "1026302";
    static final String COUPANG_TRACKING = "AF3075571";
    /** 발급 사이즈. 모바일에서는 컨테이너 폭에 맞춰 줄여 넣는다(가로 스크롤 방지). */
    static final int COUPANG_WIDTH = 680;
    static final int COUPANG_HEIGHT = 140;

    /**
     * 쿠팡 파트너스 추천 배너 마크업. SDK(g.js)는 layout.html 에서 한 번만 로드하고,
     * 여기서는 삽입 위치에 생성자 호출 스크립트만 둔다(스크립트 위치에 iframe 이 꽂힌다).
     *
     * <p>개수는 호출부가 정한다(홈 2, 그 외 1). 기기 상세처럼 고유 본문이 짧은 페이지는
     * 광고 비중이 커지면 색인에 불리할 수 있어, 색인 상태를 본 뒤에 늘린다.
     */
    static String coupangHtml() {
        return "<div class=\"coupang-slot\">\n"
             + "  <script>\n"
             + "    (function () {\n"
             + "      if (typeof PartnersCoupang === 'undefined') return;\n"
             + "      var box = document.currentScript.parentElement;\n"
             + "      var w = Math.max(300, Math.min(" + COUPANG_WIDTH + ", box.clientWidth));\n"
             + "      new PartnersCoupang.G({\n"
             + "        id: " + COUPANG_ID + ", trackingCode: \"" + COUPANG_TRACKING + "\", subId: null,\n"
             + "        template: \"carousel\", width: String(w), height: \"" + COUPANG_HEIGHT + "\"\n"
             + "      });\n"
             + "    })();\n"
             + "  </script>\n"
             + "</div>\n";
    }
}
