package co.kr.mmsoft.mmmemberservice.oauth2;

import co.kr.mmsoft.mmmemberservice.dto.RefreshTokenRecord;
import co.kr.mmsoft.mmmemberservice.jwt.JwtTokenProvider;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountMapper;
import co.kr.mmsoft.mmmemberservice.redis.RedisRefreshTokenStore;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * =====================================================================
 * [OAuth2LoginSuccessHandler.java] - OAuth2 로그인 성공 후 처리 핸들러
 * =====================================================================
 *
 * 📌 이 클래스의 역할
 *   - CustomOAuth2UserService 또는 CustomOidcUserService 처리 완료 후
 *     Spring Security가 자동으로 이 핸들러를 호출합니다.
 *   - JWT AccessToken과 RefreshToken을 발급합니다.
 *   - 프론트엔드로 리다이렉트합니다.
 *
 * 📌 왜 "임시코드" 방식을 사용하나요?
 *   - 소셜 로그인은 브라우저가 직접 location.href로 URL에 접근합니다. (동기 방식)
 *   - 이 경우 서버 응답 body를 브라우저가 그대로 화면에 출력합니다.
 *   - 그래서 AccessToken을 직접 body로 전달할 수 없습니다!
 *   ─────────────────────────────────────────
 *   - 해결책: 임시코드(UUID) 방식
 *     1. 서버가 AccessToken을 Redis에 60초간 보관
 *     2. 임시코드를 URL에 담아 프론트로 리다이렉트
 *     3. 프론트가 임시코드로 POST /api/auth/oauth2/token 요청
 *     4. 서버가 AccessToken 반환 (body로 정상 전달)
 *
 * 📌 AuthenticationSuccessHandler 인터페이스
 *   - Spring Security가 정한 "인증 성공 후 처리" 약속입니다.
 *   - onAuthenticationSuccess() 메서드를 반드시 구현해야 합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider       jwtTokenProvider;
    private final AccountMapper          accountMapper;
    private final RedisRefreshTokenStore redisRefreshTokenStore;

    // application-dev.yml의 app.frontend-url 값
    // 리다이렉트할 프론트엔드 주소 (예: "http://localhost:5173")
    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * OAuth2 로그인 성공 시 Spring Security가 자동으로 호출합니다.
     *
     * @param request        HTTP 요청 객체
     * @param response       HTTP 응답 객체 (리다이렉트, 쿠키 설정에 사용)
     * @param authentication 인증 완료된 사용자 정보 (OAuth2AuthenticationToken)
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        // authentication이 OAuth2AuthenticationToken 타입인지 확인
        // 패턴 매칭(instanceof + 변수 선언): Java 16+ 문법
        if (!(authentication instanceof OAuth2AuthenticationToken authToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OAuth2 인증 토큰이 아닙니다");
            return;
        }

        // 어떤 공급자인지 확인 (예: "google", "naver", "kakao")
        String registrationId = authToken.getAuthorizedClientRegistrationId();
        String openId;
        String email="";

        // ★ Google OIDC vs Naver/Kakao 구분하여 openId 추출 ★
        Object principal = authToken.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            // Google (OIDC 방식): OidcUser.getSubject()가 고유 식별자
            openId = oidcUser.getSubject();
        } else {
            // Naver, Kakao (OAuth2 방식): 우리가 attrs.put("openId", ...)으로 저장한 값 사용
            OAuth2User oAuth2User = (OAuth2User) principal;
            openId = (String) oAuth2User.getAttributes().get("openId");
            email = (String) oAuth2User.getAttributes().get("email");
        }

        log.debug("OAuth2 로그인 성공 - 공급자: {}, openId: {}, email:{}", registrationId, openId, email);

        // DB에서 회원 정보 조회 (자동 회원가입은 CustomOAuth2UserService에서 이미 완료)
        Account account = accountMapper.findByProviderAndOpenId(registrationId, openId);
        if (account == null) {
            log.error("OAuth2 로그인 성공했으나 account 정보 없음 - 공급자: {}, openId: {}",
                    registrationId, openId);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "회원 정보를 찾을 수 없습니다");
            return;
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 1. AccessToken 발급 (유효기간 15분)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        List<String> roles = List.of("ROLE_customer"); // 소셜 로그인 회원은 기본 customer 역할
        String accessToken = jwtTokenProvider.createAccessToken(account.getAccountId(), roles);

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 2. RefreshToken 발급 (유효기간 14일)
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String jti = UUID.randomUUID().toString(); // RefreshToken 고유 ID
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getAccountId(), jti);

        // RefreshToken을 Redis에 보안 저장 (원본 대신 SHA-256 해시값으로)
        redisRefreshTokenStore.save(
                jwtTokenProvider.sha256(refreshToken),
                new RefreshTokenRecord(account.getAccountId(), jti),
                jwtTokenProvider.refreshTtl()
        );

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 3. RefreshToken을 HttpOnly 쿠키로 전송
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // HttpOnly 쿠키: JavaScript에서 document.cookie로 접근 불가 (XSS 공격 방어)
        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true)                    // JS 접근 차단
                .secure(false)                     // http에서도 전송 허용 (개발 환경)
                .sameSite("Lax")                   // CSRF 공격 방어 설정
                .path("/api/auth/refresh")         // 이 경로에서만 쿠키 전송
                .maxAge(Duration.ofDays(14))       // 14일간 유지
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 4. AccessToken 임시코드 저장 (Redis에 60초간 보관)
        //    프론트가 이 코드로 POST /api/auth/oauth2/token 호출하면 AccessToken 반환
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        String tempCode = UUID.randomUUID().toString(); // 1회용 임시코드 (UUID)
        redisRefreshTokenStore.saveTempCode(tempCode, accessToken, 60); // 60초간 유효

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // 5. 프론트엔드 OAuth2 콜백 페이지로 리다이렉트
        //    URL 예: http://localhost:5173/oauth/callback?code=임시코드
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // URLEncoder.encode: URL에 특수문자가 있을 경우 안전하게 인코딩
        String redirectUrl = frontendUrl + "/oauth/callback?code="
                + URLEncoder.encode(tempCode, StandardCharsets.UTF_8);

        log.debug("OAuth2 로그인 성공, 프론트로 리다이렉트: {}", redirectUrl);

        // 302 Found: 브라우저에게 redirectUrl로 이동하라고 응답
        response.setStatus(HttpServletResponse.SC_FOUND);        // 302 상태 코드
        response.setHeader(HttpHeaders.LOCATION, redirectUrl);   // 이동할 URL
    }
}
