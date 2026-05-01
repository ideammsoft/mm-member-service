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
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * =====================================================================
 * [CustomOidcUserService.java] - Google OIDC 로그인 처리 서비스
 * =====================================================================
 *
 * 📌 OIDC(OpenID Connect)란?
 *   - OAuth2 위에 구축된 인증 표준 프로토콜입니다.
 *   - OAuth2: "이 사용자가 특정 리소스에 접근 권한이 있음"을 증명
 *   - OIDC  : "이 사용자가 누구인지(신원)"를 증명 (OAuth2 + 신원 정보)
 *   - Google은 OIDC를 지원하므로 표준화된 방식으로 사용자 정보를 얻을 수 있습니다.
 *
 * 📌 OIDC의 장점
 *   - oidcUser.getSubject() : 표준 고유 식별자 (sub 클레임)
 *   - oidcUser.getEmail()   : 이메일
 *   - oidcUser.getFullName(): 전체 이름
 *   - 별도 파싱 없이 편리하게 접근 가능합니다.
 *
 * 📌 OidcUserService를 delegate로 사용하는 이유
 *   - OidcUserService: Spring이 제공하는 OIDC 기본 처리 서비스
 *   - delegate(위임) 패턴: 기본 처리는 OidcUserService에게 맡기고,
 *     추가 로직(DB 저장)만 이 클래스에서 처리합니다.
 *   - 상속 대신 위임을 사용하여 더 유연한 구조를 만듭니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final ProviderMapper    providerMapper;
    private final AccountMapper     accountMapper;
    private final RoleMapper        roleMapper;
    private final AccountRoleMapper accountRoleMapper;
    private final Optional<ManymanSyncService> manymanSyncService;

    // OidcUserService: Spring이 제공하는 OIDC 기본 처리 객체 (Google 사용자 정보 조회)
    // final: 한번 생성되면 변경 불가 (불변)
    private final OidcUserService delegate = new OidcUserService();

    /**
     * Google OIDC 로그인 완료 후 사용자 정보를 처리합니다.
     * Spring Security가 Google 로그인 성공 시 자동으로 이 메서드를 호출합니다.
     *
     * @param userRequest OIDC 로그인 요청 정보
     * @return OidcUser 객체 (Google 사용자 정보 포함)
     */
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        // 기본 OIDC 처리: Google API에서 사용자 정보 가져오기 (OidcUserService에게 위임)
        OidcUser oidcUser = delegate.loadUser(userRequest);

        // 공급자 이름 확인 (Google이면 "google")
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // OIDC 표준 필드로 사용자 정보 추출 (파싱 없이 바로 사용 가능)
        String openId = oidcUser.getSubject();   // OIDC 표준 고유 식별자(sub)
        String email  = oidcUser.getEmail();     // Google 계정 이메일
        String name   = oidcUser.getFullName();  // Google 계정 이름

        log.debug("Google OIDC 로그인 - openId: {}, name: {}, email: {}", openId, name, email);

        // provider 테이블에서 "google" 공급자 정보 조회
        Provider provider = providerMapper.findByProviderName(registrationId);
        if (provider == null) {
            throw new OAuth2AuthenticationException("provider not found: " + registrationId);
        }

        // DB에서 이미 가입된 회원인지 확인
        Account account = accountMapper.findByProviderAndOpenId(registrationId, openId);

        if (account == null) {
            // 처음 로그인 → 자동 회원가입
            String homepageId = generateHomepageId(email, openId);
            account = Account.builder()
                    .openId(openId)          // Google의 sub 값 (고유 식별자)
                    .homepageId(homepageId)  // 이메일 앞부분으로 자동 생성
                    .name(name)              // Google 계정 이름
                    .email(email)            // Google 이메일
                    .provider(provider)      // Google 공급자 정보
                    .build();

            accountMapper.insert(account);

            // 신규 회원에게 "customer" 역할 부여
            Role role = roleMapper.findByRoleName("customer");
            if (role != null) {
                accountRoleMapper.insert(new AccountRole(account, role));
            }
            log.debug("Google 신규 회원가입 완료: {}, homepageId={}", name, homepageId);

        } else {
            log.debug("Google 기존 회원 로그인: {}", name);
        }

        // MSSQL manyman 동기화: email로 없으면 INSERT (phone/company는 빈칸)
        log.debug("manyman 동기화 시도 - syncService 존재: {}, email: {}", manymanSyncService.isPresent(), email);
        final String syncId = account.getHomepageId();
        manymanSyncService.ifPresent(svc -> svc.syncOAuthOnLogin(syncId, name, email));

        // Spring Security에 OidcUser 그대로 반환
        // OAuth2LoginSuccessHandler에서 oidcUser.getSubject()로 openId를 추출합니다
        return oidcUser;
    }

    private String generateHomepageId(String email, String openId) {
        String prefix;
        if (email != null && email.contains("@")) {
            prefix = email.substring(0, email.indexOf('@')).toLowerCase().replaceAll("[^a-z0-9]", "");
        } else if (openId != null && !openId.isBlank()) {
            prefix = openId.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (prefix.length() > 12) prefix = prefix.substring(0, 12);
        } else {
            prefix = "user";
        }
        if (prefix.isEmpty()) prefix = "user";
        String candidate = prefix;
        int suffix = 1;
        while (accountMapper.checkByHomepageId(candidate) > 0) {
            candidate = prefix + suffix++;
        }
        return candidate;
    }
}
