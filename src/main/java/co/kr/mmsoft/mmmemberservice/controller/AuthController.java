package co.kr.mmsoft.mmmemberservice.controller;

import co.kr.mmsoft.mmmemberservice.dto.*;
import co.kr.mmsoft.mmmemberservice.jwt.JwtTokenProvider;
import co.kr.mmsoft.mmmemberservice.member.service.AuthService;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountMapper;
import co.kr.mmsoft.mmmemberservice.redis.RedisRefreshTokenStore;
import co.kr.mmsoft.mmmemberservice.service.EmailService;
import co.kr.mmsoft.mmmemberservice.service.ManymanSyncService;
import co.kr.mmsoft.mmmemberservice.service.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

/**
 * =====================================================================
 * [AuthController.java] - 회원 인증 REST API 컨트롤러
 * =====================================================================
 *
 * 📌 컨트롤러(Controller)란?
 *   - 클라이언트(브라우저/프론트엔드)의 HTTP 요청을 받아서 처리하는 클래스입니다.
 *   - URL 경로와 메서드를 연결하는 역할을 합니다.
 *   - 비즈니스 로직은 Service에게 위임하고, 응답만 만들어서 반환합니다.
 *
 * 📌 REST API란?
 *   - HTTP 메서드(GET, POST, PUT, DELETE)와 URL로 기능을 표현하는 방식입니다.
 *   - GET    : 데이터 조회
 *   - POST   : 데이터 생성/전송
 *   - PUT    : 데이터 수정
 *   - DELETE : 데이터 삭제
 *
 * 📌 이 컨트롤러의 API 목록
 *   POST /api/auth/regist          → 회원가입
 *   POST /api/auth/login           → 일반 로그인 (아이디/비밀번호)
 *   POST /api/auth/idcheck         → 아이디 중복 확인
 *   POST /api/auth/idpassfind      → 아이디/비밀번호 찾기
 *   POST /api/auth/oauth2/token    → 소셜 로그인 임시코드 → AccessToken 교환
 *
 * 📌 어노테이션 설명
 *   @RestController  → @Controller + @ResponseBody. 모든 메서드가 JSON으로 응답합니다.
 *   @RequestMapping  → 이 컨트롤러의 기본 경로 설정 ("/api/auth")
 *   @RequiredArgsConstructor → final 필드 자동 주입 생성자 생성 (의존성 주입)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")  // 이 컨트롤러의 모든 API는 "/api/auth"로 시작합니다
@RequiredArgsConstructor
public class AuthController {

    private final AuthService             authService;
    private final AuthenticationManager   authenticationManager;
    private final RedisRefreshTokenStore  redisRefreshTokenStore;
    private final JwtTokenProvider        jwtTokenProvider;
    private final AccountMapper           accountMapper;
    private final EmailService            emailService;

    /** ppurio DB 미설정 시 null (ConditionalOnProperty) */
    @Autowired(required = false)
    private SmsService smsService;

    /** MSSQL manyman DB 미설정 시 null (ConditionalOnProperty) */
    @Autowired(required = false)
    private ManymanSyncService manymanSyncService;

    /*─────────────────────────────────────────────────────
     * [1] 회원가입 API
     * POST /api/auth/regist
     * 요청 Body: { "openId": "hong123", "password": "1234", "name": "홍길동", ... }
     * 응답:      { "msg": "ok" }
     ─────────────────────────────────────────────────────*/
    @PostMapping("/regist")
    public ResponseEntity<?> regist(@RequestBody RegistRequest registRequest) {
        // @RequestBody: 요청 body의 JSON을 RegistRequest 객체로 자동 변환
        log.debug("회원가입 요청 openId: {}", registRequest.getOpenId());
        log.debug("회원가입 요청 name: {}", registRequest.getName());

        // 서비스에게 회원가입 처리 위임
        try {
            authService.regist(registRequest);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(409).body(Map.of("msg", e.getMessage()));
        }

        // ResponseEntity.ok(): 200 OK 상태코드로 응답
        // Map.of("msg", "ok"): { "msg": "ok" } JSON으로 자동 변환
        return ResponseEntity.ok(Map.of("msg", "ok"));
    }

    /*─────────────────────────────────────────────────────
     * [2] 일반 로그인 API
     * POST /api/auth/login
     * 요청 Body: { "homepageId": "hong123", "password": "1234" }
     * 응답 Body: { "accessToken": "eyJ..." }
     * 응답 쿠키: refreshToken=eyJ... (HttpOnly)
     ─────────────────────────────────────────────────────*/
    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_JSON_VALUE  // 요청이 JSON 형식이어야 함
    )
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        log.debug("로그인 요청 homepageId: {}", loginRequest.getHomepageId());

        // 서비스에서 Spring Security 인증 + JWT 발급 처리
        AuthTokens authTokens;
        try {
            authTokens = authService.login(loginRequest);
        } catch (Exception e) {
            log.error("로그인 실패: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return ResponseEntity.status(401)
                    .body(Map.of("message", "아이디 또는 비밀번호가 올바르지 않습니다."));
        }

        // AccessToken은 응답 body에 담아서 전달
        // (프론트에서 메모리/localStorage에 보관 후 API 호출 시 Authorization 헤더에 첨부)
        LoginResponse loginResponse = new LoginResponse(authTokens.getAccessToken(), authTokens.getName(), authTokens.getEmail(), authTokens.getRoleName(), authTokens.getAccountId(), authTokens.getExpiryDate());

        // RefreshToken은 HttpOnly 쿠키로 전달
        // HttpOnly: JavaScript에서 접근 불가 → XSS 공격으로부터 보호
        ResponseCookie cookie = ResponseCookie.from("refreshToken", authTokens.getRefreshToken())
                .httpOnly(true)                    // JS 접근 차단
                .secure(false)                     // http에서도 전송 허용 (운영환경에서는 true)
                .sameSite("Lax")                   // 같은 사이트에서만 쿠키 전송 (CSRF 방어)
                .path("/api/auth/refresh")         // 이 경로(재발급 API)에서만 쿠키를 서버로 전송
                .maxAge(Duration.ofDays(14))       // 14일 후 자동 만료
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString()) // 쿠키를 응답 헤더에 추가
                .body(loginResponse);                               // body에 AccessToken 담기
    }


    /*─────────────────────────────────────────────────────
     * [3] ManDeul 연장기한 조회 API (로그인 전 공개)
     * GET /api/auth/expiry?id={homepageId}
     * 응답: "2027-03-05" 또는 "" (미등록/미설정)
     ─────────────────────────────────────────────────────*/
    @GetMapping("/expiry")
    public ResponseEntity<String> expiry(@RequestParam String id) {
        if (manymanSyncService == null || id == null || id.isBlank())
            return ResponseEntity.ok("");
        String expiry = manymanSyncService.getExpiryDate(id.trim());
        return ResponseEntity.ok(expiry != null ? expiry : "");
    }

    /*─────────────────────────────────────────────────────
     * [3-1] manyman.version 원본 조회 (VB6 레거시: 신/구 만료일 방식 구분자)
     * GET /api/auth/version?id={id}
     * 응답: version 원본 문자열(예: "API", "?1?2027-03-05!") 또는 ""
     ─────────────────────────────────────────────────────*/
    @GetMapping("/version")
    public ResponseEntity<String> version(@RequestParam String id) {
        if (manymanSyncService == null || id == null || id.isBlank())
            return ResponseEntity.ok("");
        String v = manymanSyncService.getVersion(id.trim());
        return ResponseEntity.ok(v != null ? v : "");
    }

    /*─────────────────────────────────────────────────────
     * [4] 아이디 중복 확인 API
     * POST /api/auth/idcheck
     * 요청 Body: { "openId": "hong123" }
     * 응답:      { "count": 0 }  → 0이면 사용 가능, 1이면 중복
     ─────────────────────────────────────────────────────*/
    @PostMapping("/idcheck")
    public ResponseEntity<?> idcheck(@RequestBody IdCheckRequest idcheckRequest) {
        log.info("idcheck openId=[{}]", idcheckRequest.getOpenId());
        int result = authService.idCheck(idcheckRequest.getOpenId());
        log.info("idcheck result={} for openId=[{}]", result, idcheckRequest.getOpenId());
        // IdCheckResponse: { "count": 결과값 } 형태의 DTO
        return ResponseEntity.ok(new IdCheckResponse(result));
    }

    /*─────────────────────────────────────────────────────
     * [4] 아이디/비밀번호 찾기 API
     * POST /api/auth/idpassfind
     * 요청 Body:
     *   아이디 찾기: { "email": "hong@test.com", "idOrPass": "id" }
     *   비번 찾기:   { "openId": "hong123", "email": "hong@test.com", "idOrPass": "pass" }
     * 응답:
     *   아이디 찾기: { "foundId": "hong123" }
     *   비번 찾기:   { "newPassword": "ok" }
     ─────────────────────────────────────────────────────*/
    @PostMapping("/idpassfind")
    public ResponseEntity<?> idpassfind(@RequestBody IdPassFindRequest request) {
        log.debug("아이디찾기/비밀번호찾기 - openId: {}, email: {}", request.getOpenId(), request.getEmail());

        // idOrPass 값으로 아이디 찾기인지, 비밀번호 찾기인지 구분
        String idOrPass = request.getIdOrPass();

        if ("id".equals(idOrPass)) {
            // 아이디 찾기
            String foundId = authService.idFind(request.getEmail(), request.getPhone());
            return ResponseEntity.ok(
                    Map.of("foundId", foundId != null ? foundId : "없음")
            );

        } else {
            // 비밀번호 찾기 (임시 비밀번호 발급)
            String newPassword = authService.passwordFind(request);
            log.debug("임시 비밀번호 발급 결과: {}", newPassword);

            if (newPassword != null && !newPassword.isEmpty()) {
                boolean hasPhone = request.getPhone() != null && !request.getPhone().isBlank();
                boolean hasEmail = request.getEmail() != null && !request.getEmail().isBlank();

                if (hasPhone && smsService != null) {
                    // 휴대폰으로 찾기: SMS 발송
                    try {
                        smsService.sendTempPassword(request.getPhone(), newPassword);
                    } catch (Exception e) {
                        log.error("SMS 발송 실패: {}", e.getMessage());
                    }
                } else {
                    // 이메일로 찾기 (또는 SMS 서비스 미설정 시 이메일 fallback)
                    String targetEmail = null;
                    if (hasEmail) {
                        targetEmail = request.getEmail();
                    } else if (request.getOpenId() != null && !request.getOpenId().isBlank()) {
                        Account account = accountMapper.findByOpenId(request.getOpenId());
                        if (account != null && account.getEmail() != null) {
                            targetEmail = account.getEmail();
                        }
                    }
                    if (targetEmail != null) {
                        try {
                            emailService.sendTempPassword(targetEmail, newPassword);
                        } catch (Exception e) {
                            log.error("이메일 발송 실패: {}", e.getMessage());
                        }
                    }
                }
            }

            return ResponseEntity.ok(
                    Map.of("newPassword", (newPassword != null && !newPassword.isEmpty()) ? "ok" : "")
            );
        }
    }

    /*─────────────────────────────────────────────────────
     * [5] OAuth2 소셜 로그인 임시코드 → AccessToken 교환 API
     * POST /api/auth/oauth2/token
     * 요청 Body: { "code": "UUID임시코드" }
     * 응답:      { "accessToken": "eyJ..." }
     *
     * 📌 이 API가 필요한 이유
     *   - 소셜 로그인 후 브라우저가 /oauth/callback?code=UUID 페이지로 이동
     *   - 프론트의 /oauth/callback 페이지에서 이 API를 호출하여 실제 AccessToken 획득
     *   - 임시코드는 1회용이며 60초 안에 사용해야 합니다
     ─────────────────────────────────────────────────────*/
    @PostMapping("/oauth2/token")
    public ResponseEntity<?> exchangeOAuth2TempCode(@RequestBody Map<String, String> body) {
        // 요청 body에서 "code" 값 꺼내기
        String code = body.get("code");

        // 임시코드가 없는 경우 400 Bad Request 반환
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code가 없습니다"));
        }

        // Redis에서 임시코드로 AccessToken 조회 (1회용 - 조회 후 자동 삭제)
        String accessToken = redisRefreshTokenStore.consumeTempCode(code);

        // 임시코드가 없거나 만료된 경우 401 Unauthorized 반환
        if (accessToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "유효하지 않거나 만료된 코드입니다"));
        }

        log.debug("OAuth2 임시코드 교환 성공");
        // 성공: AccessToken을 body에 담아 응답
        return ResponseEntity.ok(new LoginResponse(accessToken, null, null));
    }

    /*─────────────────────────────────────────────────────
     * [6] OAuth2 임시코드 교환 (query param 버전) - 사용자 정보 포함
     * POST /api/auth/oauth2/exchange?code=UUID임시코드
     * 응답: { accessToken, name, email, phone, company, provider, isNewMember }
     *
     * phone이 비어있으면 isNewMember=true → 프론트에서 추가정보 입력 화면 표시
     ─────────────────────────────────────────────────────*/
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<?> exchangeOAuth2Code(@RequestParam("code") String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "code가 없습니다"));
        }

        String accessToken = redisRefreshTokenStore.consumeTempCode(code);
        if (accessToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "유효하지 않거나 만료된 코드입니다"));
        }

        // JWT에서 accountId 추출 후 회원 정보 조회
        Long accountId = Long.parseLong(jwtTokenProvider.validateAndGetClaims(accessToken).getSubject());
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            return ResponseEntity.status(500).body(Map.of("error", "회원 정보를 찾을 수 없습니다"));
        }

        String provider = account.getProvider() != null
                          ? account.getProvider().getProviderName() : "local";

        log.debug("OAuth2 exchange 성공 - accountId: {}, isNewMember: {}",
                  accountId, (account.getMphone() == null || account.getMphone().isBlank()));

        return ResponseEntity.ok(new OAuthExchangeResponse(
                accessToken,
                account.getName(),
                account.getEmail(),
                account.getMphone(),
                account.getPhone(),
                account.getCompany(),
                provider,
                account.getHomepageId()
        ));
    }

    /** 이메일 미설정 회원에게 이메일 저장 (PATCH /api/auth/me/email) */
    @PatchMapping("/me/email")
    public ResponseEntity<?> updateMyEmail(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim();
        if (email.isEmpty()) return ResponseEntity.badRequest().body(Map.of("message", "이메일을 입력해 주세요."));
        if (authHeader == null || !authHeader.startsWith("Bearer "))
            return ResponseEntity.status(401).body(Map.of("message", "인증이 필요합니다."));
        try {
            Long accountId = Long.parseLong(
                    jwtTokenProvider.validateAndGetClaims(authHeader.substring(7)).getSubject());
            int updated = accountMapper.updateEmailIfEmpty(accountId, email);
            return ResponseEntity.ok(Map.of("success", updated > 0));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "토큰이 유효하지 않습니다."));
        }
    }

    /** 광고 페이지 문의 폼 메일 발송 (POST /api/auth/contact) */
    @PostMapping("/contact")
    public ResponseEntity<?> contact(@RequestBody Map<String, String> body) {
        try {
            String type   = body.getOrDefault("type",   "");
            String name   = body.getOrDefault("name",   "");
            String region = body.getOrDefault("region", "");
            String phone  = body.getOrDefault("phone",  "");
            if (name.isBlank() || phone.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "성명과 연락처는 필수입니다."));
            }
            emailService.sendContactInquiry(type, name, region, phone);
            return ResponseEntity.ok(Map.of("msg", "ok"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "발송 실패: " + e.getMessage()));
        }
    }
}
