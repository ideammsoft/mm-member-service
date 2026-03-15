package co.kr.mmsoft.mmmemberservice.oauth2;

import java.util.Map;

/**
 * =====================================================================
 * [GoogleUserInfo.java] - Google OIDC 응답 파싱 클래스
 * =====================================================================
 *
 * 📌 Google 로그인 응답 형식 (OIDC 표준)
 *   Google은 OpenID Connect(OIDC) 표준을 사용하므로 응답이 단순합니다:
 *   {
 *     "sub"  : "116572396312341234",   ← 고유 식별자 (subject)
 *     "name" : "홍길동",
 *     "email": "hong@gmail.com",
 *     "picture": "https://..."
 *   }
 *
 * 📌 implements OAuth2UserInfo
 *   - OAuth2UserInfo 인터페이스를 구현합니다.
 *   - openId(), name(), email() 메서드를 반드시 구현해야 합니다.
 */
public class GoogleUserInfo implements OAuth2UserInfo {

    // Google API로부터 받은 사용자 정보 (key-value 형태의 Map)
    private final Map<String, Object> attributes;

    /**
     * 생성자 - Google 응답 데이터를 받아서 저장합니다.
     *
     * @param attributes Google이 반환한 사용자 정보 Map
     */
    public GoogleUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Google 고유 사용자 ID를 반환합니다.
     * OIDC 표준에서 "sub"(subject) 필드가 고유 식별자입니다.
     */
    @Override
    public String openId() {
        // "sub" 키의 값을 String으로 변환하여 반환
        // 예: "116572396312341234"
        return (String) attributes.get("sub");
    }

    /**
     * Google 계정의 사용자 이름을 반환합니다.
     */
    @Override
    public String name() {
        return (String) attributes.get("name");
    }

    /**
     * Google 계정의 이메일 주소를 반환합니다.
     */
    @Override
    public String email() {
        return (String) attributes.get("email");
    }
}
