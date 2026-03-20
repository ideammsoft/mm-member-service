package co.kr.mmsoft.mmmemberservice.oauth2;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.AccountRole;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Provider;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Role;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountMapper;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountRoleMapper;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.ProviderMapper;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.RoleMapper;
import co.kr.mmsoft.mmmemberservice.service.ManymanSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * =====================================================================
 * [CustomOAuth2UserService.java] - Naver/Kakao OAuth2 로그인 처리 서비스
 * =====================================================================
 *
 * 📌 이 클래스의 역할
 *   - Naver, Kakao 소셜 로그인 완료 후 Spring Security가 자동으로 호출합니다.
 *   - 공급자로부터 받은 사용자 정보를 파싱하여 우리 DB에 저장/조회합니다.
 *   - 처음 로그인하는 사용자는 자동으로 회원가입 처리합니다.
 *
 * 📌 Google은 왜 이 클래스에서 처리하지 않나요?
 *   - Google은 OIDC(OpenID Connect) 표준을 사용합니다.
 *   - OIDC는 OAuth2보다 표준화된 프로토콜이라 별도 처리가 필요합니다.
 *   - Google은 CustomOidcUserService.java에서 처리합니다.
 *
 * 📌 DefaultOAuth2UserService를 상속하는 이유
 *   - Spring Security의 기본 OAuth2 사용자 정보 조회 로직을 재사용합니다.
 *   - super.loadUser(userRequest)가 공급자 API를 호출하고 사용자 정보를 가져옵니다.
 *   - 우리는 그 이후 로직(DB 저장/조회)만 추가로 작성하면 됩니다.
 *
 * 📌 OAuth2 로그인 전체 흐름
 *   1. 사용자가 "네이버로 로그인" 버튼 클릭
 *   2. 브라우저가 네이버 로그인 페이지로 이동
 *   3. 네이버에서 로그인 후 우리 서버로 리다이렉트 (인증 코드 포함)
 *   4. Spring Security가 인증 코드로 네이버 API에서 Access Token 획득
 *   5. Spring Security가 Access Token으로 네이버 사용자 정보 API 호출
 *   6. ★ 이 메서드(loadUser)가 호출됨 ★ - 사용자 정보 처리
 *   7. 처리 완료 후 OAuth2LoginSuccessHandler.onAuthenticationSuccess() 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    // DB 접근용 Mapper 객체들
    private final ProviderMapper    providerMapper;
    private final AccountMapper     accountMapper;
    private final RoleMapper        roleMapper;
    private final AccountRoleMapper accountRoleMapper;
    private final Optional<ManymanSyncService> manymanSyncService;

    /**
     * 소셜 공급자로부터 사용자 정보를 가져오고 DB에 저장/조회합니다.
     * Spring Security가 OAuth2 로그인 완료 후 자동으로 이 메서드를 호출합니다.
     *
     * @param userRequest OAuth2 로그인 요청 정보 (공급자명, 액세스토큰 등 포함)
     * @return DefaultOAuth2User 객체 (Spring Security가 인증 주체로 사용)
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // 부모 클래스의 loadUser를 호출하여 공급자 API에서 사용자 정보 가져오기
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 어떤 공급자인지 확인 (application-dev.yml의 registration 키와 일치)
        // 예: "naver", "kakao"
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // Google은 OIDC 방식으로 처리하므로 이 서비스에서는 기본 처리만 하고 반환
        // (CustomOidcUserService에서 별도 처리됨)
        if ("google".equals(registrationId)) {
            return super.loadUser(userRequest);
        }

        // 공급자별 응답 형식을 통일된 인터페이스로 변환
        // Naver면 NaverUserInfo, Kakao면 KakaoUserInfo 객체 생성
        OAuth2UserInfo info = OAuth2UserInfoFactory.from(registrationId, oAuth2User.getAttributes());
        log.debug("OAuth2 로그인 - 공급자: {}, openId: {}, name: {}", registrationId, info.openId(), info.name(), info.email());

        // provider 테이블에서 공급자 정보 조회
        // 예) registrationId = "naver" → DB에서 provider_name='naver' 조회
        Provider provider = providerMapper.findByProviderName(registrationId);
        if (provider == null) {
            // provider 테이블에 해당 공급자가 없으면 에러 (V2__seed_data.sql에서 미리 추가해야 함)
            throw new OAuth2AuthenticationException("provider not found: " + registrationId);
        }

        // 우리 DB에서 이미 가입된 회원인지 확인
        // 공급자명 + openId 조합으로 유일한 회원을 식별합니다
        Account account = accountMapper.findByProviderAndOpenId(registrationId, info.openId());

        if (account == null) {
            // ★ 처음 로그인하는 경우 → 자동 회원가입 ★
            account = Account.builder()
                    .openId(info.openId())    // 공급자의 고유 ID
                    .name(info.name())        // 공급자가 제공한 이름
                    .email(info.email())      // 공급자가 제공한 이메일
                    .provider(provider)       // 공급자 정보 (google/naver/kakao)
                    // password는 null (SNS 로그인은 비밀번호 없음)
                    .build();

            accountMapper.insert(account); // DB에 새 회원 저장

            // 신규 회원에게 "customer" 역할 부여
            Role role = roleMapper.findByRoleName("customer");
            if (role != null) {
                accountRoleMapper.insert(new AccountRole(account, role));
            }
            log.debug("OAuth2 신규 회원가입 완료: {}", info.name());
        } else {
            // 이미 가입된 회원인 경우 → 그냥 로그인 처리
            log.debug("OAuth2 기존 회원 로그인: {}", info.name());
        }

        // MSSQL manyman 동기화: email로 없으면 INSERT (phone/company는 빈칸)
        manymanSyncService.ifPresent(svc -> svc.syncOAuthOnLogin(info.email(), info.name()));

        // Spring Security가 사용할 권한 목록 (일단 customer 역할 부여)
        Collection<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_customer")
        );

        // Spring Security에 전달할 사용자 정보 Map 구성
        // 공급자 원본 속성 + 우리가 추가한 정보를 합칩니다
        Map<String, Object> attrs = new HashMap<>(oAuth2User.getAttributes());
        attrs.put("openId",    info.openId());         // 우리가 사용할 고유 ID
        attrs.put("provider",  registrationId);        // 공급자 이름
        attrs.put("accountId", account.getAccountId()); // 우리 DB의 account_id

        // DefaultOAuth2User 반환
        // 두 번째 매개변수(attrs)의 세 번째 매개변수("openId")가 "주 속성 키"입니다.
        // 나중에 authentication.getName()을 호출하면 openId 값이 반환됩니다.
        return new DefaultOAuth2User(authorities, attrs, "openId");
    }
}
