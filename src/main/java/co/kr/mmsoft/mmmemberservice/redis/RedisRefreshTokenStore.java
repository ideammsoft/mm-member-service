package co.kr.mmsoft.mmmemberservice.redis;

import co.kr.mmsoft.mmmemberservice.dto.RefreshTokenRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RedisRefreshTokenStore {
    //Spring에서 지원하는 Redis와 관련되 CRUD 수행 객체
    private final StringRedisTemplate redisTemplate;

    /*----------------------------------------------------
    리프레시 토큰 저장(유효기간 TTL 지정하여 저장)
    1) 어떤 RT를? rt:refreshhash 누구것인지?(accountId) id값 jti "jti값"
        ex) rt:dfjksdfklsdfjklsdfjkl accountIdR "34" jti "dfjsdfksdfjsdfklj"
    2) 추후 검색을 위해 미리 목록을 만들어 놓기
        어떤 유저가 가진 RT인지...
        ex) rt:user:accountId dfjksdfklsdfjklsdfjkl
            rt;user:45 dfjksdfklsdfjklsdfjkl
            rt;user:45 asdfsddfsdfsdfasfasdf
            rt;user:45 3433sdfsdfsdfsdf44444  --> 같은 회원일 경우 기기마다 틀림. 모두 무효화 가능
    ----------------------------------------------------*/

    public void save(String refreshhash, RefreshTokenRecord record, Duration ttl) {
        redisTemplate.opsForHash().putAll("rt:" + refreshhash, Map.of("accountId", String.valueOf(record.accountId()), "jti", record.jti()));
        redisTemplate.expire("rt:" + refreshhash, ttl); //만료시간 설정

        //추후 검색을 위한 목록 만들어 놓기
        // cmd명령창)  SADD rt:user:23 해시화된 refreshtoken
        redisTemplate.opsForSet().add("rt:" + record.accountId(), refreshhash);
        redisTemplate.expire("rt:" + record.accountId(), ttl.plusDays(1));
    }
}
