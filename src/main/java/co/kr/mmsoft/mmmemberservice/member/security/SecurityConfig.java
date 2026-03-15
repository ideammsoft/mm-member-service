package co.kr.mmsoft.mmmemberservice.member.security;

import co.kr.mmsoft.mmmemberservice.jwt.JwtAuthenticationFilter;
import co.kr.mmsoft.mmmemberservice.oauth2.CustomOAuth2UserService;
import co.kr.mmsoft.mmmemberservice.oauth2.CustomOidcUserService;
import co.kr.mmsoft.mmmemberservice.oauth2.OAuth2LoginSuccessHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * =====================================================================
 * [SecurityConfig.java] - Spring Security 전체 보안 설정 클래스
 * =====================================================================
 *
 * 📌 이 클래스가 하는 일
 *   1. "어떤 URL은 로그인 없이 접근 가능한가?" 설정
 *   2. JWT 필터 등록 (모든 요청에서 토큰 검사)
 *   3. OAuth2 소셜 로그인 설정 (Google, Naver, Kakao)
 *   4. CORS 정책 설정 (프론트엔드가 서버에 요청할 수 있게 허용)
 *   5. 세션 비활성화 (JWT 방식이므로 서버에 세션 저장 불필요)
 *
 * 📌 CORS(Cross-Origin Resource Sharing)란?
 *   - 브라우저 보안 정책: 기본적으로 다른 도메인으로의 AJAX 요청을 차단합니다.
 *   - 프론트(localhost:5173)에서 백엔드(localhost:1991)로 요청할 때 CORS 오류 발생
 *   - 서버에서 "이 출처(Origin)는 허용해줘"라고 설정해야 합니다.
 *
 * 📌 @Configuration 어노테이션
 *   - 이 클래스가 "설정 파일"임을 스프링에게 알립니다.
 *   - 내부의 @Bean 메서드들이 스프링 빈으로 등록됩니다.
 *
 * 📌 @EnableWebSecurity 어노테이션
 *   - Spring Security 기능을 활성화합니다.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // application-dev.yml의 app.frontend-url 값 주입
    // 예) "http://localhost:5173"
    @Value("${app.frontend-url}")
    private String frontendUrl;

    // JWT 검증 필터 (매 요청마다 Authorization 헤더의 토큰을 검사)
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Naver, Kakao OAuth2 로그인 처리 서비스
    private final CustomOAuth2UserService customOAuth2UserService;

    // Google OIDC 로그인 처리 서비스
    private final CustomOidcUserService customOidcUserService;

    // OAuth2 로그인 성공 후 JWT 발급 및 리다이렉트 처리
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

    /**
     * 비밀번호 암호화 방식 설정
     *
     * 📌 BCryptPasswordEncoder란?
     *   - BCrypt 해시 알고리즘으로 비밀번호를 암호화합니다.
     *   - 같은 비밀번호여도 매번 다른 해시값이 생성됩니다. (솔트(salt) 포함)
     *   - 해킹으로 DB를 탈취해도 원본 비밀번호를 알 수 없습니다.
     *
     * @return BCrypt 암호화 방식의 PasswordEncoder 빈
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Spring Security의 인증 관리자(AuthenticationManager) 빈 등록
     *
     * 📌 AuthenticationManager란?
     *   - authenticationManager.authenticate(아이디+비밀번호)를 호출하면
     *     Spring Security가 자동으로 로그인 검증을 수행합니다.
     *   - AuthService.login()에서 사용합니다.
     *
     * @param config Spring Security 설정 객체 (자동 주입)
     * @return AuthenticationManager 인스턴스
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * CORS(교차 출처 리소스 공유) 정책 설정
     *
     * 📌 설정 내용
     *   - 허용할 프론트엔드 출처: application-dev.yml의 app.frontend-url
     *   - 허용할 HTTP 메서드: GET, POST, PUT, DELETE, OPTIONS
     *   - 자격증명(쿠키) 허용: true (RefreshToken 쿠키 전송을 위해 필수)
     *
     * @return CORS 설정 빈
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // 허용할 출처(Origin) 설정
        // 주의: "*" (와일드카드)은 allowCredentials(true)와 함께 사용 불가!
        //       반드시 정확한 URL을 지정해야 합니다.
        config.setAllowedOrigins(List.of(frontendUrl)); // 예) "http://localhost:5173"

        // 허용할 HTTP 메서드
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 허용할 요청 헤더 ("*" = 모든 헤더 허용)
        config.setAllowedHeaders(List.of("*"));

        // 자격증명(쿠키, Authorization 헤더 등) 포함 허용
        // RefreshToken 쿠키가 요청에 포함되려면 true여야 합니다
        config.setAllowCredentials(true);

        // preflight 요청(OPTIONS) 캐시 시간 (초)
        // 3600초 = 1시간 동안 같은 CORS 조건이면 OPTIONS 요청을 반복하지 않음
        config.setMaxAge(3600L);

        // "/api/**" 경로에만 위 CORS 설정 적용
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Spring Security 보안 필터 체인 설정
     * 이것이 핵심 보안 설정입니다.
     *
     * @param http HttpSecurity 객체 (보안 설정 빌더)
     * @return 완성된 SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        // CSRF 보호 비활성화
        // CSRF: 사이트 간 요청 위조 공격
        // JWT 방식에서는 CSRF 공격이 어려우므로 비활성화해도 됩니다
        http.csrf(csrf -> csrf.disable());

        // HTTP Basic 인증 비활성화 (브라우저 기본 로그인 팝업 비활성화)
        http.httpBasic(basic -> basic.disable());

        // 폼 로그인 비활성화 (Spring Security 기본 로그인 페이지 비활성화)
        // 우리는 REST API 방식으로 로그인하므로 불필요
        http.formLogin(form -> form.disable());

        // CORS는 Gateway에서 처리하므로 member-service에서는 비활성화
        // (Gateway + member-service 양쪽에서 CORS 헤더 추가 시 중복 발생 → 브라우저 거부)
        http.cors(cors -> cors.disable());

        // URL별 접근 권한 설정
        http.authorizeHttpRequests(auth -> auth
                // OPTIONS 메서드: CORS preflight 요청 → 모든 경로 허용
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // /api/auth/** : 로그인, 회원가입, 아이디/비밀번호 찾기 → 누구나 접근 가능
                .requestMatchers("/api/auth/**").permitAll()

                // /login/oauth2/**, /oauth2/** : 소셜 로그인 리다이렉트 경로 → 누구나 접근 가능
                .requestMatchers("/login/oauth2/**").permitAll()
                .requestMatchers("/oauth2/**").permitAll()

                // /error : 스프링 에러 페이지 → 허용
                .requestMatchers("/error").permitAll()

                // 그 외 모든 경로 → 로그인(인증) 필요
                .anyRequest().authenticated()
        );

        // 세션 생성 정책: STATELESS
        // JWT 방식은 서버에 세션을 저장하지 않습니다.
        // 각 요청마다 JWT 토큰으로 본인 확인을 합니다.
        http.sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        // OAuth2 소셜 로그인 설정
        http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                        // Naver, Kakao 로그인 처리 서비스 등록
                        .userService(customOAuth2UserService)
                        // Google OIDC 로그인 처리 서비스 등록
                        .oidcUserService(customOidcUserService)
                )
                // 소셜 로그인 성공 후 실행할 핸들러 등록 (JWT 발급 + 리다이렉트)
                .successHandler(oAuth2LoginSuccessHandler)
        );

        // JWT 인증 필터 등록
        // UsernamePasswordAuthenticationFilter 앞에 실행되도록 등록합니다
        // 모든 요청이 컨트롤러에 도달하기 전에 JWT 검증을 먼저 수행합니다
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
