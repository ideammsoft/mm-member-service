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

@Component
public class JwtTokenProvider {

    // 수정 포인트: 필드 레벨의 @Value를 제거했습니다.
    // 생성자에서 변환 후 이 변수에 할당하므로 필드에는 타입만 정의합니다.
    private final SecretKey key;

    private final long accessTokenMinutes;
    private final long refreshTokenMinutes;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-minutes}") long accessTokenMinutes,
            @Value("${app.jwt.refresh-token-minutes}") long refreshTokenMinutes
    ){
        // 우리의 실제 key는 OS에 환경변수에 String 즉 문자열로 들어있음..
        // 따라서 개발자가 String으로 존재하는 키를 SecretKey로 직접 변경해야 함
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenMinutes = accessTokenMinutes;
        this.refreshTokenMinutes = refreshTokenMinutes;
    }

    /*--------------------------------------------
    Access Token 발급
    --------------------------------------------*/
    public String createAccessToken(long accountId, List<String> roles){
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(accessTokenMinutes*60);
        return Jwts.builder()
                .subject(Long.toString(accountId)) //이 토큰의 주인
                .claim("type", "access") //이 토큰의 유형
                .claim("roles", roles) //Role+Permission(네트워크 트래픽 때문에 가볍게 가는게 좋다)
                .id(UUID.randomUUID().toString()) //이토큰의 구분ID
                .issuedAt(Date.from(now)) //발생 시간
                .expiration(Date.from(exp)) //만료일
                .signWith(key) //비대칭키(열쇠/자물쇠), 여기서는 대칭키
                .compact();
    }

    /*------------------------------------------------
    Epoch(에포크) 초 란? 1970년 1월 1일 0시 0분을 기준으로 지금까지 흐른 시간을 초 단위로
    세고 있는 절대적 기준 시각.
    -------------------------------------------------*/
    public long refreshExpEpochSeconds(){
        return Instant.now().plusSeconds(refreshTokenMinutes*60).getEpochSecond();
    }

    //Redis API가 Duration형을 하용하므로
    public Duration refreshTtl(){
        return Duration.ofMinutes(refreshTokenMinutes);
    }

    /*--------------------------------------------
    Refresh Token 발급
    --------------------------------------------*/
    public String createRefreshToken(long accountId, String jti){
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(refreshTokenMinutes*60);
        return Jwts.builder()
                .subject(Long.toString(accountId))
                .claim("type","refresh")
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    /*--------------------------------------------
    해석 및 검증
    1) MalformedJwtException : JWT(Header.Payload.Signature) 형식에 맞지 않을 경우 발생되는 예외
    2) SignatureException : 서명이 다를 경우
    3) ExpiredJwtException : 유효시간이 지난 경우
    --------------------------------------------*/
    public Claims validateAndGetClaims(String token){
        return Jwts.parser()
                .verifyWith(key) //서버가 가진 대칭키로 검증준비
                .build()
                .parseSignedClaims(token)//검증 실행(서명 및 만료시간 체크) 이 시점에 여러 Exception 발생됨
                .getPayload();
    }

    /*--------------------------------------------
    RefreshToken 원본을 해시값으로 변경시키는 메서드
    목적; RefreshToken 원본을 Redis에 그대로 저장했을때, 혹시 모를 해킹 피해를 줄이기 위해
    --------------------------------------------*/
    public String sha256(String rawToken){
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            // %x(소문자로 출력하시오) 10 a, 15 f
            //02 (무조건 2자리를 채우시오)
            for (byte b : hashed) sb.append(String.format("%02x", b)); //64글자를 일정하게 채우기
            return sb.toString();
        } catch (Exception e){
            throw new IllegalArgumentException("SHA-256으로 변환 실패",e);
        }
    }
}