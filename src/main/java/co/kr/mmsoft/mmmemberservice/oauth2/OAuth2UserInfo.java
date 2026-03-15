package co.kr.mmsoft.mmmemberservice.oauth2;

/**
 * =====================================================================
 * [OAuth2UserInfo.java] - 소셜 로그인 사용자 정보 인터페이스 (추상화)
 * =====================================================================
 *
 * 📌 인터페이스(Interface)란?
 *   - "이 기능은 반드시 구현해야 한다"는 약속/설계도입니다.
 *   - 실제 코드는 없고, "어떤 메서드가 있어야 하는가"만 정의합니다.
 *
 * 📌 왜 인터페이스가 필요한가요?
 *   - Google, Naver, Kakao가 반환하는 사용자 정보 형식이 모두 다릅니다.
 *     - Google: { "sub": "...", "email": "...", "name": "..." }
 *     - Naver:  { "response": { "id": "...", "email": "...", "name": "..." } }
 *     - Kakao:  { "id": 123, "properties": { "nickname": "..." }, ... }
 *   - 각각 다른 방식으로 파싱해야 하지만, 결국 필요한 정보는 동일합니다.
 *   - 인터페이스로 "openId, name, email을 제공해야 한다"고 약속하면,
 *     나머지 코드에서는 공급자 구분 없이 동일하게 사용할 수 있습니다.
 *
 * 📌 구현 클래스들
 *   - GoogleUserInfo.java : Google 응답 파싱 담당
 *   - NaverUserInfo.java  : Naver 응답 파싱 담당
 *   - KakaoUserInfo.java  : Kakao 응답 파싱 담당
 */
public interface OAuth2UserInfo {

    /**
     * 소셜 공급자가 제공하는 사용자 고유 식별값을 반환합니다.
     * account 테이블의 open_id 컬럼에 저장됩니다.
     *
     * @return 고유 식별자
     *   - Google: OIDC의 sub (subject) 값 (예: "116572...")
     *   - Naver : response.id 값 (예: "12345678")
     *   - Kakao : id 값 (Long을 String으로 변환, 예: "123456789")
     */
    String openId();

    /**
     * 소셜 공급자가 제공하는 사용자 이름을 반환합니다.
     *
     * @return 사용자 이름
     *   - Google: name 필드
     *   - Naver : response.name 필드
     *   - Kakao : properties.nickname 필드
     */
    String name();

    /**
     * 소셜 공급자가 제공하는 이메일 주소를 반환합니다.
     * Kakao의 경우 이메일 제공에 동의하지 않으면 null일 수 있습니다.
     *
     * @return 이메일 주소 (없으면 null)
     */
    String email();
}
