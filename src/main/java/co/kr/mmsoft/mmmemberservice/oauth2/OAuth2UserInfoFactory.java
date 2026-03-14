package co.kr.mmsoft.mmmemberservice.oauth2;

import java.util.Map;

/**
 * =====================================================================
 * [OAuth2UserInfoFactory.java] - 공급자별 OAuth2UserInfo 구현체 생성 팩토리
 * =====================================================================
 *
 * 📌 팩토리(Factory) 패턴이란?
 *   - "공장"처럼 필요한 객체를 만들어주는 패턴입니다.
 *   - "google"이라고 주면 GoogleUserInfo를, "naver"라고 주면 NaverUserInfo를 생성합니다.
 *   - 호출하는 코드가 어떤 구현체를 사용할지 알 필요 없이 공장에 맡깁니다.
 *
 * 📌 사용 위치
 *   - CustomOAuth2UserService: 소셜 로그인 처리 시 공급자별 파싱
 *   - OAuth2LoginSuccessHandler: 로그인 성공 후 사용자 정보 추출 시 사용
 *
 * 📌 switch 표현식 (Java 14+)
 *   - "case 값 -> 결과" 형태의 현대적인 switch 문법입니다.
 *   - break 없이도 각 케이스가 독립적으로 실행됩니다.
 */
public class OAuth2UserInfoFactory {

    // 인스턴스 생성을 막는 private 생성자 (모든 메서드가 static이므로)
    private OAuth2UserInfoFactory() {}

    /**
     * 공급자 이름에 따라 적절한 OAuth2UserInfo 구현체를 생성하여 반환합니다.
     *
     * @param registrationId 소셜 공급자 이름 (예: "google", "naver", "kakao")
     * @param attributes     해당 공급자가 반환한 사용자 정보 Map
     * @return 공급자별 파싱 구현체 (모두 OAuth2UserInfo 인터페이스를 구현)
     * @throws IllegalArgumentException 지원하지 않는 공급자인 경우
     */
    public static OAuth2UserInfo from(String registrationId, Map<String, Object> attributes) {
        // switch 표현식: registrationId 값에 따라 다른 구현체 반환
        return switch (registrationId.toLowerCase()) {
            case "google" -> new GoogleUserInfo(attributes); // Google 전용 파서
            case "naver"  -> new NaverUserInfo(attributes);  // Naver 전용 파서 (중첩 response 처리)
            case "kakao"  -> new KakaoUserInfo(attributes);  // Kakao 전용 파서 (Long id 처리)
            // 지원하지 않는 공급자가 요청된 경우 예외 발생
            default -> throw new IllegalArgumentException("지원하지 않는 OAuth2 공급자: " + registrationId);
        };
    }
}
