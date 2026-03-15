package co.kr.mmsoft.mmmemberservice.jwt;

import co.kr.mmsoft.mmmemberservice.member.principal.CustomUserDetails;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * =====================================================================
 * [JwtAuthenticationFilter.java] - 매 요청마다 JWT 토큰을 검사하는 필터
 * =====================================================================
 *
 * 📌 필터(Filter)란?
 *   - 클라이언트의 요청이 컨트롤러에 도달하기 전에 먼저 실행되는 코드입니다.
 *   - 마치 건물 입구의 "보안 검색대"와 같습니다.
 *   - 통과하면 컨트롤러로, 통과 못하면 401 에러를 반환합니다.
 *
 * 📌 OncePerRequestFilter란?
 *   - "요청 1번당 딱 1번만 실행되는 필터"라는 뜻입니다.
 *   - 스프링이 같은 요청에 필터를 중복 실행하는 것을 방지합니다.
 *
 * 📌 이 필터의 작동 흐름
 *   1. 요청의 Authorization 헤더에서 "Bearer 토큰값" 추출
 *   2. 토큰 유효성 검증 (만료/위변조 체크)
 *   3. 유효하면 → SecurityContext에 "이 사람은 로그인된 사람입니다" 기록
 *   4. 다음 필터 또는 컨트롤러로 요청을 전달
 *
 * 📌 SecurityContext란?
 *   - 현재 요청을 처리하는 스레드에 "로그인 정보"를 임시 저장하는 공간입니다.
 *   - 컨트롤러에서 로그인한 사용자 정보를 꺼낼 때 이 공간에서 가져옵니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final AccountMapper accountMapper;

    /**
     * 이 필터를 건너뛸(제외할) 경로를 지정합니다.
     * true를 반환하면 이 필터를 실행하지 않고 바로 다음으로 넘어갑니다.
     *
     * 📌 제외하는 이유
     *   - 로그인, 회원가입 등은 토큰 없이도 접근 가능해야 합니다.
     *   - OAuth2 로그인 경로도 초기 진입 시 토큰이 없으므로 제외합니다.
     *
     * @param request HTTP 요청 객체
     * @return true이면 이 필터 건너뜀, false이면 이 필터 실행
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri    = request.getRequestURI(); // 요청 경로 (예: "/api/auth/login")
        String method = request.getMethod();      // HTTP 메서드 (GET, POST 등)

        // OPTIONS 요청: 브라우저가 CORS 사전 확인을 위해 보내는 요청 → 항상 통과
        // /api/auth/** : 로그인, 회원가입, 아이디찾기 등 인증 불필요 경로 → 통과
        // /login/oauth2, /oauth2 : 소셜 로그인 리다이렉트 경로 → 통과
        return "OPTIONS".equalsIgnoreCase(method)
                || uri.startsWith("/api/auth/login")
                || uri.startsWith("/api/auth/regist")
                || uri.startsWith("/api/auth/idcheck")
                || uri.startsWith("/api/auth/idpassfind")
                || uri.startsWith("/api/auth/refresh")
                || uri.startsWith("/api/auth/oauth2/")  // token, exchange 모두 포함
                || uri.startsWith("/login/oauth2")
                || uri.startsWith("/oauth2");
    }

    /**
     * 실제 JWT 검증 로직을 실행합니다.
     * shouldNotFilter()가 false를 반환했을 때만 이 메서드가 호출됩니다.
     *
     * @param request     HTTP 요청 (헤더, URI 등 포함)
     * @param response    HTTP 응답 (에러 전송에 사용)
     * @param filterChain 다음 필터 또는 컨트롤러로 전달하기 위한 체인
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 요청 헤더에서 Authorization 값 꺼내기
        // 형식: "Bearer eyJ0eXAi..."
        String header = request.getHeader("Authorization");

        // Authorization 헤더가 없거나 "Bearer "로 시작하지 않으면
        // 토큰 없이 접근하는 것으로 판단 → 그냥 다음으로 넘김 (permitAll 경로는 통과됨)
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return; // 이 메서드 종료 (아래 코드 실행 안 함)
        }

        // "Bearer " (7글자) 이후의 순수 토큰 문자열만 추출
        // 예) "Bearer eyJ..." → "eyJ..."
        String token = header.substring(7).trim();

        try {
            // 토큰 검증 + Claims(내용) 추출
            // 만료되었거나 위변조된 토큰이면 여기서 예외 발생
            Claims claims = jwtTokenProvider.validateAndGetClaims(token);

            // 토큰 유형이 "access"인지 확인
            // RefreshToken으로 API를 호출하려는 시도를 막습니다
            String tokenType = claims.get("type", String.class);
            if (!"access".equals(tokenType)) {
                // 401 Unauthorized 에러 전송 후 메서드 종료
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access Token이 아닙니다");
                return;
            }

            // SecurityContext에 아직 인증 정보가 없을 때만 설정합니다
            // (중복 설정 방지)
            if (SecurityContextHolder.getContext().getAuthentication() == null) {

                // 토큰의 subject = accountId(PK) 숫자 문자열
                // findByAccountId로 조회 (findByOpenId로 하면 OAuth 사용자에서 버그 발생)
                String accountIdStr = claims.getSubject();
                Account account = accountMapper.findByAccountId(Long.parseLong(accountIdStr));
                if (account == null) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "회원 정보를 찾을 수 없습니다");
                    return;
                }
                CustomUserDetails userDetails = new CustomUserDetails(account);

                // Spring Security에게 "이 요청은 로그인된 사용자의 요청입니다"라고 알림
                // UsernamePasswordAuthenticationToken: Spring Security의 인증 정보 객체
                // 매개변수: (사용자정보, 비밀번호(null=이미검증됨), 권한목록)
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());

                // SecurityContext에 인증 정보 저장
                // 이후 컨트롤러에서 SecurityContextHolder.getContext().getAuthentication()으로 꺼낼 수 있음
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("JWT 인증 성공 - accountId: {}", accountIdStr);
            }

            // 인증 완료! 다음 필터 또는 컨트롤러로 요청 전달
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            // 토큰이 만료된 경우: 클라이언트에게 재발급 요청하라는 신호
            log.debug("JWT 만료: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "만료된 토큰입니다");

        } catch (JwtException | IllegalArgumentException e) {
            // 위변조, 형식 오류, 서명 불일치 등
            log.debug("JWT 유효하지 않음: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "유효하지 않은 토큰입니다");
        }
    }
}
