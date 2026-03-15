package co.kr.mmsoft.mmmemberservice.oauth2;

import java.util.Map;

/**
 * =====================================================================
 * [NaverUserInfo.java] - Naver OAuth2 응답 파싱 클래스
 * =====================================================================
 *
 * 📌 Naver 로그인 응답 형식 (중첩 구조 주의!)
 *   Naver는 사용자 정보가 "response" 키 안에 중첩되어 있습니다:
 *   {
 *     "resultcode": "00",
 *     "message"   : "success",
 *     "response"  : {             ← 여기 안에 실제 사용자 정보가 있음
 *       "id"   : "12345678",
 *       "name" : "홍길동",
 *       "email": "hong@naver.com",
 *       "nickname": "홍씨"
 *     }
 *   }
 *
 * 📌 @SuppressWarnings("unchecked")
 *   - Map 안에서 Map을 꺼낼 때 Java가 타입 경고를 발생시킵니다.
 *   - 이 어노테이션은 그 경고를 무시합니다. (코드가 올바르다고 개발자가 보증)
 */
@SuppressWarnings("unchecked")
public class NaverUserInfo implements OAuth2UserInfo {

    // Naver 응답의 "response" 키 안의 실제 사용자 정보
    private final Map<String, Object> response;

    /**
     * 생성자 - 전체 attributes에서 "response" 부분만 추출하여 저장합니다.
     *
     * @param attributes Naver가 반환한 전체 사용자 정보 Map
     */
    public NaverUserInfo(Map<String, Object> attributes) {
        // "response" 키의 값을 꺼내서 캐스팅
        // (String, Object) Map을 (String, Object) Map으로 변환
        this.response = (Map<String, Object>) attributes.get("response");
    }

    /**
     * Naver 고유 사용자 ID를 반환합니다.
     * Naver는 "response.id" 필드가 고유 식별자입니다.
     */
    @Override
    public String openId() {
        return (String) response.get("id");
    }

    /**
     * Naver 계정의 사용자 이름을 반환합니다.
     */
    @Override
    public String name() {
        return (String) response.get("name");
    }

    /**
     * Naver 계정의 이메일 주소를 반환합니다.
     */
    @Override
    public String email() {
        return (String) response.get("email");
    }
}
