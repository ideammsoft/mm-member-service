package co.kr.mmsoft.mmmemberservice.nice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NICE 본인인증 (구형 CheckPlus v1) 서비스
 *
 * 흐름:
 *   1. request()  → encData 생성 → 프론트에 반환
 *   2. 프론트가 NICE 팝업 오픈 (form POST to NICE 서버)
 *   3. NICE → 우리 서버 success endpoint POST (EncodeData)
 *   4. success() → 복호화 → Redis에 requestNo 키로 결과 저장 → 완료 페이지 반환
 *   5. 프론트 팝업이 부모에게 결과 전달 후 닫힘
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NiceAuthService {

    private final StringRedisTemplate redisTemplate;

    @Value("${nice.site-code:AB501}")
    private String siteCode;

    @Value("${nice.site-password:nlGcSndlMVTw}")
    private String sitePassword;

    @Value("${nice.return-url:https://m.mmsoft.co.kr/api/auth/nice/success}")
    private String returnUrl;

    @Value("${nice.error-url:https://m.mmsoft.co.kr/api/auth/nice/fail}")
    private String errorUrl;

    private static final String NICE_FORM_URL =
            "https://nice.checkplus.co.kr/CheckPlusSafeModel/checkplus.cb";
    private static final Duration RESULT_TTL = Duration.ofMinutes(5);
    private static final String REDIS_PREFIX  = "nice:auth:";

    // ──────────────────────────────────────────────
    // 1. 인증 요청 데이터 생성
    // ──────────────────────────────────────────────
    public NiceAuthRequestData buildRequest(String authType) {
        String requestNo = UUID.randomUUID().toString().replace("-", "").substring(0, 30);

        String plainData = buildPlainData(requestNo, authType);
        String encData   = NiceSeedCrypto.encrypt(plainData, siteCode, sitePassword);

        log.info("[NICE] siteCode={}, sitePassword={}", siteCode, sitePassword);
        log.info("[NICE] plainData={}", plainData);
        log.info("[NICE] encData length={}, encData={}", encData.length(), encData);

        // 요청번호를 Redis에 임시 저장 (결과 수신 시 대조용)
        redisTemplate.opsForValue().set(REDIS_PREFIX + "req:" + requestNo, "pending", RESULT_TTL);

        return NiceAuthRequestData.builder()
                .formUrl(NICE_FORM_URL)
                .siteCode(siteCode)
                .encData(encData)
                .requestNo(requestNo)
                .build();
    }

    // ──────────────────────────────────────────────
    // 2. 성공 콜백 처리 (NICE → 우리 서버)
    // ──────────────────────────────────────────────
    public NiceAuthResult processSuccess(String encodeData) {
        try {
            String plain = NiceSeedCrypto.decrypt(encodeData, siteCode, sitePassword);
            log.info("NICE 복호화 결과: {}", plain);

            Map<String, String> fields = parsePlainData(plain);

            NiceAuthResult result = NiceAuthResult.builder()
                    .success(true)
                    .requestNo(fields.getOrDefault("REQ_SEQ", ""))
                    .name(fields.getOrDefault("UTF8_NAME", fields.getOrDefault("NAME", "")))
                    .birthDate(fields.getOrDefault("BIRTHDATE", ""))
                    .gender(fields.getOrDefault("GENDER", ""))
                    .mobileNo(fields.getOrDefault("MOBILE_NO", ""))
                    .di(fields.getOrDefault("DI", ""))
                    .ci(fields.getOrDefault("CI", ""))
                    .build();

            // 결과를 Redis에 저장 (프론트가 requestNo로 조회)
            if (!result.getRequestNo().isEmpty()) {
                String key = REDIS_PREFIX + "result:" + result.getRequestNo();
                redisTemplate.opsForValue().set(key,
                        toJson(result), RESULT_TTL);
            }
            return result;

        } catch (Exception e) {
            log.error("NICE 인증 결과 복호화 실패", e);
            return NiceAuthResult.builder().success(false).errorMsg(e.getMessage()).build();
        }
    }

    // ──────────────────────────────────────────────
    // 3. 프론트에서 requestNo로 결과 조회
    // ──────────────────────────────────────────────
    public NiceAuthResult getResult(String requestNo) {
        String key = REDIS_PREFIX + "result:" + requestNo;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        redisTemplate.delete(key); // 1회용
        return fromJson(json);
    }

    // ──────────────────────────────────────────────
    // private helpers
    // ──────────────────────────────────────────────
    private String buildPlainData(String requestNo, String authType) {
        return field("REQ_SEQ",     requestNo)
             + field("SITECODE",    siteCode)
             + field("AUTH_TYPE",   authType.isEmpty() ? "M" : authType)
             + field("RTN_URL",     returnUrl)
             + field("ERR_URL",     errorUrl)
             + field("POPUP_GUBUN", "N")
             + field("CUSTOMIZE",   "");
    }

    /** NICE 키-값 포맷: "키이름길이:키이름값길이:값" */
    private String field(String key, String value) {
        return key.length() + ":" + key + value.length() + ":" + value;
    }

    /** 복호화된 평문 파싱 */
    private Map<String, String> parsePlainData(String plain) {
        Map<String, String> map = new HashMap<>();
        int i = 0;
        while (i < plain.length()) {
            int colon1 = plain.indexOf(':', i);
            if (colon1 < 0) break;
            int keyLen = Integer.parseInt(plain.substring(i, colon1));
            String key = plain.substring(colon1 + 1, colon1 + 1 + keyLen);
            int colon2 = colon1 + 1 + keyLen;
            int colon3 = plain.indexOf(':', colon2);
            if (colon3 < 0) break;
            int valLen = Integer.parseInt(plain.substring(colon2, colon3));
            String val = plain.substring(colon3 + 1, colon3 + 1 + valLen);
            map.put(key, val);
            i = colon3 + 1 + valLen;
        }
        return map;
    }

    private String toJson(NiceAuthResult r) {
        return String.format(
                "{\"success\":%b,\"requestNo\":\"%s\",\"name\":\"%s\",\"birthDate\":\"%s\"," +
                "\"gender\":\"%s\",\"mobileNo\":\"%s\",\"di\":\"%s\",\"ci\":\"%s\"}",
                r.isSuccess(), esc(r.getRequestNo()), esc(r.getName()),
                esc(r.getBirthDate()), esc(r.getGender()), esc(r.getMobileNo()),
                esc(r.getDi()), esc(r.getCi()));
    }

    private NiceAuthResult fromJson(String json) {
        // 간단한 파서 (Jackson 없이)
        return NiceAuthResult.builder()
                .success(json.contains("\"success\":true"))
                .requestNo(extract(json, "requestNo"))
                .name(extract(json, "name"))
                .birthDate(extract(json, "birthDate"))
                .gender(extract(json, "gender"))
                .mobileNo(extract(json, "mobileNo"))
                .di(extract(json, "di"))
                .ci(extract(json, "ci"))
                .build();
    }

    private String extract(String json, String key) {
        String token = "\"" + key + "\":\"";
        int s = json.indexOf(token);
        if (s < 0) return "";
        s += token.length();
        int e = json.indexOf('"', s);
        return e < 0 ? "" : json.substring(s, e);
    }

    private String esc(String s) { return s == null ? "" : s.replace("\"", "\\\""); }
}
