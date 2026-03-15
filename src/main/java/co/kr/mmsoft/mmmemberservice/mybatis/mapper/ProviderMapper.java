package co.kr.mmsoft.mmmemberservice.mybatis.mapper;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Provider;
import org.apache.ibatis.annotations.Mapper;

/**
 * =====================================================================
 * [ProviderMapper.java] - provider 테이블 조회 매퍼 인터페이스
 * =====================================================================
 *
 * 📌 이 파일이 뭔가요?
 *   - provider 테이블을 조회하는 MyBatis Mapper 인터페이스입니다.
 *   - 실제 SQL은 ProviderMapper.xml에 있습니다.
 *
 * 📌 사용 위치
 *   - AuthService: 홈페이지 회원가입 시 "homepage" 공급자 조회
 *   - CustomOAuth2UserService: 소셜 로그인 시 "google"/"naver"/"kakao" 공급자 조회
 *   - CustomOidcUserService: Google OIDC 로그인 시 "google" 공급자 조회
 */
@Mapper
public interface ProviderMapper {

    /**
     * 공급자 이름으로 Provider 정보를 조회합니다.
     *
     * @param providerName 조회할 공급자 이름 (예: "google", "naver", "kakao", "homepage")
     * @return 찾은 Provider 객체 (providerId, providerName 포함)
     *         V2__seed_data.sql에서 미리 데이터를 넣어두었으므로 항상 찾을 수 있습니다.
     */
    Provider findByProviderName(String providerName);
}
