package co.kr.mmsoft.mmmemberservice.redis;

import co.kr.mmsoft.mmmemberservice.dto.RefreshTokenRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * =====================================================================
 * [RedisRefreshTokenStore.java] - Redis에 RefreshToken을 저장/조회하는 클래스
 * =====================================================================
 *
 * 📌 Redis란?
 *   - 메모리(RAM)에 데이터를 저장하는 데이터베이스입니다.
 *   - 일반 DB(MySQL)보다 훨씬 빠르고, 만료 시간(TTL) 설정이 가능합니다.
 *   - key-value 구조: "키" → "값" 형태로 저장합니다.
 *   - 예) "rt:abc123" → {"accountId": "42", "jti": "uuid값"}
 *
 * 📌 왜 RefreshToken을 Redis에 저장하나요?
 *   - 서버를 재시작해도 RefreshToken이 유지됩니다.
 *   - TTL(만료 시간)을 설정해두면 Redis가 자동으로 삭제합니다.
 *   - 보안 강화: 원본 토큰 대신 해시값을 저장합니다.
 *   - 강제 로그아웃 기능: Redis에서 삭제하면 해당 토큰 무효화 가능합니다.
 *
 * 📌 Redis 키 설계 구조
 *   rt:{해시값}            → {"accountId": "42", "jti": "uuid값"}  (RefreshToken 본체)
 *   rt:{accountId}         → {해시값1, 해시값2, ...}               (이 회원의 토큰 목록)
 *   oauth2:code:{임시코드} → "AccessToken 문자열"                   (OAuth2 임시코드)
 *
 * 📌 StringRedisTemplate이란?
 *   - Spring이 제공하는 Redis CRUD 전용 객체입니다.
 *   - opsForValue() : 단순 문자열 저장/조회
 *   - opsForHash()  : Map(딕셔너리) 형태 저장/조회
 *   - opsForSet()   : 집합(Set) 형태 저장/조회
 */
@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore {

    // Spring이 자동으로 주입해주는 Redis 작업 객체
    private final StringRedisTemplate redisTemplate;

    /**
     * RefreshToken을 Redis에 저장합니다.
     *
     * 📌 저장하는 두 가지 정보
     *   1. 토큰 본체: rt:{해시값} → {accountId, jti}
     *      - 나중에 RefreshToken의 유효성 검증에 사용
     *   2. 토큰 목록: rt:{accountId} → {해시값들}
     *      - 이 회원의 모든 RefreshToken을 한번에 무효화할 때 사용
     *
     * @param refreshHash 해시화된 RefreshToken (SHA-256으로 변환한 값)
     * @param record      저장할 데이터 (accountId + jti)
     * @param ttl         만료 시간 (Duration 객체, 예: 14일)
     */
    public void save(String refreshHash, RefreshTokenRecord record, Duration ttl) {
        // 1) RefreshToken 본체 저장 (Hash 구조로 저장)
        //    key: "rt:abc123abc..."  value: {accountId: "42", jti: "uuid값"}
        redisTemplate.opsForHash().putAll(
                "rt:" + refreshHash,  // Redis 키
                Map.of(               // 저장할 Map 데이터
                        "accountId", String.valueOf(record.accountId()),
                        "jti",       record.jti()
                )
        );
        // 만료 시간 설정 (TTL이 지나면 Redis가 자동으로 삭제)
        redisTemplate.expire("rt:" + refreshHash, ttl);

        // 2) 이 회원의 토큰 목록에 추가 (Set 구조로 저장)
        //    key: "rt:42"  value: {"abc123...", "def456...", ...}
        //    같은 회원이 여러 기기에서 로그인하면 각기 다른 해시값이 추가됩니다
        redisTemplate.opsForSet().add("rt:" + record.accountId(), refreshHash);
        // 토큰 목록은 RefreshToken 보다 하루 더 길게 보관
        redisTemplate.expire("rt:" + record.accountId(), ttl.plusDays(1));
    }

    /**
     * OAuth2 소셜 로그인 후 AccessToken 교환을 위한 임시코드를 저장합니다.
     *
     * 📌 왜 임시코드가 필요한가요?
     *   - 소셜 로그인 완료 후 브라우저가 서버로 리다이렉트됩니다. (location.href 방식)
     *   - 이 방식은 서버가 응답 body에 데이터를 담아도 브라우저가 화면에 출력해버립니다.
     *   - 따라서 AccessToken을 직접 전달할 수 없습니다.
     *   - 해결책: UUID 임시코드를 URL에 담아 프론트로 보내고,
     *             프론트가 이 코드로 POST 요청해서 실제 AccessToken을 받아갑니다.
     *
     * @param code        임시코드 (UUID 문자열)
     * @param accessToken 교환해줄 실제 AccessToken
     * @param ttlSeconds  임시코드 유효 시간 (초) - 보통 60초 (1분)
     */
    public void saveTempCode(String code, String accessToken, long ttlSeconds) {
        // key: "oauth2:code:uuid값"  value: "AccessToken 문자열"
        // 60초 후 자동으로 Redis에서 삭제됩니다
        redisTemplate.opsForValue().set(
                "oauth2:code:" + code,  // Redis 키
                accessToken,             // 저장할 값
                Duration.ofSeconds(ttlSeconds) // 만료 시간
        );
    }

    /**
     * 임시코드로 AccessToken을 1회 조회합니다. (조회 후 자동 삭제)
     *
     * 📌 "1회용"인 이유
     *   - 보안을 위해 임시코드는 한 번 사용되면 즉시 삭제합니다.
     *   - 악용 방지: 코드를 가로채더라도 이미 사용되었으면 재사용 불가
     *
     * @param code 프론트에서 전달받은 임시코드
     * @return AccessToken 문자열 (없거나 만료되면 null 반환)
     */
    public String consumeTempCode(String code) {
        String key = "oauth2:code:" + code;

        // Redis에서 AccessToken 조회
        String accessToken = redisTemplate.opsForValue().get(key);

        // 조회 성공 시 즉시 삭제 (1회용)
        if (accessToken != null) {
            redisTemplate.delete(key);
        }

        return accessToken; // 없으면 null 반환
    }
}
