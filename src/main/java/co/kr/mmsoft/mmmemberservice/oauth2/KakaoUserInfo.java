package co.kr.mmsoft.mmmemberservice.oauth2;

import java.util.Map;

/**
 * =====================================================================
 * [KakaoUserInfo.java] - Kakao OAuth2 응답 파싱 클래스
 * =====================================================================
 *
 * 📌 Kakao 로그인 응답 형식 (복잡한 중첩 구조)
 *   Kakao는 응답이 여러 단계로 중첩되어 있습니다:
 *   {
 *     "id": 123456789,               ← 고유 식별자 (Long 타입!)
 *     "properties": {
 *       "nickname": "홍길동"          ← 이름은 여기에
 *     },
 *     "kakao_account": {
 *       "email": "hong@kakao.com",   ← 이메일은 여기에 (동의 시에만)
 *       "email_needs_agreement": false
 *     }
 *   }
 *
 * 📌 Kakao 주의사항
 *   - "id"는 Long(숫자) 타입이므로 .toString()으로 변환해야 합니다.
 *   - 이메일은 사용자가 제공에 동의하지 않으면 null입니다.
 */
@SuppressWarnings("unchecked")
public class KakaoUserInfo implements OAuth2UserInfo {

    // Kakao API로부터 받은 전체 사용자 정보
    private final Map<String, Object> attributes;

    /**
     * 생성자
     * @param attributes Kakao가 반환한 사용자 정보 Map
     */
    public KakaoUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * Kakao 고유 사용자 ID를 문자열로 반환합니다.
     * Kakao의 id는 Long(숫자) 타입이므로 String으로 변환합니다.
     */
    @Override
    public String openId() {
        Object id = attributes.get("id");
        // null 체크 후 숫자를 문자열로 변환 (예: 123456789L → "123456789")
        return id != null ? id.toString() : null;
    }

    /**
     * Kakao 사용자 닉네임을 반환합니다.
     * "properties" → "nickname" 경로에 있습니다.
     */
    @Override
    public String name() {
        // "properties" 맵을 꺼내기
        Map<String, Object> properties = (Map<String, Object>) attributes.get("properties");
        if (properties != null) {
            return (String) properties.get("nickname");
        }
        return null; // properties가 없으면 null 반환
    }

    /**
     * Kakao 계정 이메일을 반환합니다.
     * "kakao_account" → "email" 경로에 있으며, 동의하지 않으면 null입니다.
     */
    @Override
    public String email() {
        // "kakao_account" 맵을 꺼내기
        Map<String, Object> kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
        if (kakaoAccount != null) {
            return (String) kakaoAccount.get("email");
        }
        return null; // 동의 안 했거나 없으면 null 반환
    }
}
