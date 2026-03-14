package co.kr.mmsoft.mmmemberservice.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * =====================================================================
 * [JwtTokenProvider.java] - JWT 토큰 발급 및 검증 담당 클래스
 * =====================================================================
 *
 * 📌 JWT란 무엇인가요? (JSON Web Token)
 *   - 로그인 성공 후 서버가 발급하는 "디지털 출입증"입니다.
 *   - 클라이언트(브라우저)가 이 토큰을 가지고 있으면 로그인된 사용자로 인정합니다.
 *   - 세 부분으로 구성됩니다: Header.Payload.Signature
 *     예) eyJ0eXAi.eyJzdWIi.SflKxw...
 *
 * 📌 AccessToken vs RefreshToken
 *   - AccessToken  : API 호출에 사용하는 짧은 수명(15분)의 토큰
 *                    (짧은 이유: 탈취되어도 금방 만료되어 피해 최소화)
 *   - RefreshToken : AccessToken이 만료되면 새로 발급받기 위한 긴 수명(14일) 토큰
 *                    Redis에 저장하여 관리합니다
 *
 * 📌 application-dev.yml에서 값을 가져오는 방법
 *   @Value("${app.jwt.secret}") → yml 파일의 app.jwt.secret 값을 자동으로 주입
 *
 * 📌 @Component 어노테이션
 *   - 이 클래스를 스프링 빈(Bean)으로 등록합니다.
 *   - 다른 클래스에서 @Autowired 또는 생성자 주입으로 사용할 수 있습니다.
 */
@Component
public class JwtTokenProvider {

    // JWT 서명에 사용할 비밀키 (SecretKey 타입)
    // application-dev.yml의 app.jwt.secret 값으로 만들어집니다
    private final SecretKey key;

    // AccessToken 유효시간 (분 단위) - yml에서 15로 설정
    private final long accessTokenMinutes;

    // RefreshToken 유효시간 (분 단위) - yml에서 20160(=14일)으로 설정
    private final long refreshTokenMinutes;

    /**
     * 생성자 - yml 파일의 설정값을 주입받아 초기화합니다.
     *
     * @param secret              Base64로 인코딩된 JWT 서명 비밀키 문자열
     * @param accessTokenMinutes  AccessToken 유효 시간 (분)
     * @param refreshTokenMinutes RefreshToken 유효 시간 (분)
     */
    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
            @Value("${app.jwt.refresh-token-minutes}") long refreshTokenMinutes) {

        // Base64로 인코딩된 문자열을 바이트 배열로 디코딩하여 SecretKey 생성
        // HMAC-SHA 알고리즘에 사용할 수 있는 키 형태로 변환합니다
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);

        this.accessTokenMinutes  = accessTokenMinutes;
        this.refreshTokenMinutes = refreshTokenMinutes;
    }

    /**
     * AccessToken을 발급합니다.
     *
     * 📌 토큰 안에 담기는 정보 (Payload/Claims)
     *   - subject  : 회원 번호 (accountId) - 이 토큰의 주인을 식별
     *   - type     : "access" - 이 토큰이 AccessToken임을 표시
     *   - roles    : 역할 목록 (예: ["ROLE_customer"])
     *   - jti      : 토큰 고유 ID (중복 방지)
     *   - iat      : 발급 시각
     *   - exp      : 만료 시각 (발급 시각 + 15분)
     *
     * @param accountId 회원 번호 (토큰의 주인)
     * @param roles     역할 목록 (예: ["ROLE_customer", "NOTICE_VIEW"])
     * @return 완성된 JWT 문자열 (예: "eyJ0eXAi...")
     */
    public String createAccessToken(long accountId, List<String> roles) {
        Instant now = Instant.now();                           // 현재 시각
        Instant exp = now.plusSeconds(accessTokenMinutes * 60); // 만료 시각 (현재 + 분→초 변환)

        return Jwts.builder()
                .subject(Long.toString(accountId)) // 토큰의 주인 (회원 번호를 문자열로)
                .claim("type", "access")           // 토큰 유형 표시
                .claim("roles", roles)             // 역할 목록
                .id(UUID.randomUUID().toString())  // 토큰 고유 ID (UUID: 전세계 유일한 값)
                .issuedAt(Date.from(now))          // 발급 시각
                .expiration(Date.from(exp))        // 만료 시각
                .signWith(key)                     // 비밀키로 서명 (위변조 방지)
                .compact();                        // JWT 문자열로 변환
    }

    /**
     * RefreshToken을 발급합니다.
     *
     * 📌 RefreshToken은 AccessToken보다 단순합니다.
     *   - 역할(roles) 정보를 담지 않습니다 (AccessToken 재발급 용도로만 사용)
     *   - 유효기간이 14일로 길어 Redis에 안전하게 보관합니다
     *
     * @param accountId 회원 번호
     * @param jti       이 RefreshToken의 고유 ID (AccessToken 재발급 시 검증에 사용)
     * @return 완성된 JWT RefreshToken 문자열
     */
    public String createRefreshToken(long accountId, String jti) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(refreshTokenMinutes * 60);

        return Jwts.builder()
                .subject(Long.toString(accountId)) // 토큰의 주인
                .claim("type", "refresh")          // 토큰 유형 표시
                .id(jti)                           // 외부에서 받은 고유 ID (UUID)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /**
     * RefreshToken의 만료 시간을 Duration 형태로 반환합니다.
     * Redis에 저장할 때 만료 시간(TTL) 설정에 사용합니다.
     *
     * @return Duration 객체 (예: 14일)
     */
    public Duration refreshTtl() {
        return Duration.ofMinutes(refreshTokenMinutes);
    }

    /**
     * JWT 토큰을 검증하고 내부 정보(Claims/Payload)를 반환합니다.
     *
     * 📌 이 메서드에서 검증하는 것들
     *   1. 서명이 올바른가? (우리 서버의 비밀키로 만든 토큰인가?)
     *   2. 만료되지 않았는가? (exp 시각이 지나지 않았는가?)
     *   3. 형식이 올바른가? (Header.Payload.Signature 구조인가?)
     *
     * 📌 검증 실패 시 예외 발생
     *   - ExpiredJwtException  : 만료된 토큰
     *   - SignatureException   : 서명 불일치 (위변조 의심)
     *   - MalformedJwtException: 잘못된 형식
     *
     * @param token 검증할 JWT 문자열
     * @return Claims 객체 (토큰 안에 담긴 정보들: subject, roles, exp 등)
     */
    public Claims validateAndGetClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)      // 서버의 비밀키로 서명 검증 준비
                .build()
                .parseSignedClaims(token) // 실제 검증 실행 (실패 시 예외 발생)
                .getPayload();            // 검증 통과 시 Payload(Claims) 반환
    }

    /**
     * RefreshToken 원본을 SHA-256 해시값으로 변환합니다.
     *
     * 📌 왜 해시값으로 변환하나요?
     *   - RefreshToken 원본을 Redis에 그대로 저장하면, Redis가 해킹될 경우
     *     토큰을 바로 사용할 수 있어 보안 위험이 있습니다.
     *   - 해시는 일방향 변환이라 원본을 복구할 수 없습니다.
     *   - 비교할 때는 입력된 토큰을 다시 해시하여 저장된 해시값과 비교합니다.
     *
     * @param rawToken 해시로 변환할 원본 토큰 문자열
     * @return SHA-256 해시값 (64자리 16진수 문자열)
     */
    public String sha256(String rawToken) {
        try {
            // SHA-256 해시 알고리즘 인스턴스 생성
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // 문자열을 바이트 배열로 변환 후 해시 계산
            byte[] hashed = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));

            // 바이트 배열을 16진수 문자열로 변환 (항상 64자리)
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b)); // 예: 255 → "ff"
            }
            return sb.toString(); // 64자리 해시 문자열 반환
        } catch (Exception e) {
            throw new IllegalArgumentException("SHA-256 변환 실패", e);
        }
    }
}
