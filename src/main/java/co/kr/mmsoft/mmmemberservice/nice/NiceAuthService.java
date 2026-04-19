package co.kr.mmsoft.mmmemberservice.nice;

import NiceID.Check.CPClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

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

    @Value("${nice.return-url:https://www.mmsoft.co.kr/api/auth/nice/success}")
    private String returnUrl;

    @Value("${nice.error-url:https://www.mmsoft.co.kr/api/auth/nice/fail}")
    private String errorUrl;

    private static final String NICE_FORM_URL =
            "https://nice.checkplus.co.kr/CheckPlusSafeModel/checkplus.cb";
    private static final Duration RESULT_TTL = Duration.ofMinutes(5);
    private static final String REDIS_PREFIX  = "nice:auth:";

    // ──────────────────────────────────────────────
    // 1. 인증 요청 데이터 생성
    // ──────────────────────────────────────────────
    public NiceAuthRequestData buildRequest(String authType) {
        try {
            CPClient niceCheck = new CPClient();

            String requestNo = niceCheck.getRequestNO(siteCode);
            if (requestNo == null || requestNo.isEmpty()) {
                requestNo = "REQ" + System.currentTimeMillis();
            }

            String sAuthType = (authType == null || authType.isEmpty()) ? "M" : authType;

            String sPlainData = "7:REQ_SEQ" + requestNo.getBytes().length + ":" + requestNo
                    + "8:SITECODE" + siteCode.getBytes().length + ":" + siteCode
                    + "9:AUTH_TYPE" + sAuthType.getBytes().length + ":" + sAuthType
                    + "7:RTN_URL" + returnUrl.getBytes().length + ":" + returnUrl
                    + "7:ERR_URL" + errorUrl.getBytes().length + ":" + errorUrl
                    + "9:CUSTOMIZE" + "0:";

            log.info("[NICE] siteCode={}, returnUrl={}", siteCode, returnUrl);
            log.info("[NICE] plainData={}", sPlainData);

            int iReturn = niceCheck.fnEncode(siteCode, sitePassword, sPlainData);
            if (iReturn != 0) {
                throw new RuntimeException("NICE 암호화 실패: " + iReturn);
            }

            String encData = niceCheck.getCipherData();
            log.info("[NICE] encData length={}", encData.length());

            // 요청번호를 Redis에 임시 저장
            redisTemplate.opsForValue().set(REDIS_PREFIX + "req:" + requestNo, "pending", RESULT_TTL);

            return NiceAuthRequestData.builder()
                    .formUrl(NICE_FORM_URL)
                    .siteCode(siteCode)
                    .encData(encData)
                    .requestNo(requestNo)
                    .build();

        } catch (Exception e) {
            log.error("NICE 인증 요청 생성 실패", e);
            throw new RuntimeException("NICE 인증 요청 생성 실패: " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────
    // 2. 성공 콜백 처리 (NICE → 우리 서버)
    // ──────────────────────────────────────────────
    public NiceAuthResult processSuccess(String encodeData) {
        try {
            // Base64에서 + 가 URL 디코딩 시 공백으로 변환되는 경우 복원
            encodeData = encodeData.replace(" ", "+");

            log.info("[NICE] processSuccess siteCode={}, pwLen={}, dataLen={}",
                    siteCode, sitePassword != null ? sitePassword.length() : -1, encodeData.length());
            log.info("[NICE] processSuccess encodeData prefix=[{}]", encodeData.substring(0, Math.min(40, encodeData.length())));
            log.info("[NICE] JCE providers: {}", java.util.Arrays.stream(java.security.Security.getProviders()).map(p -> p.getName()).collect(java.util.stream.Collectors.joining(",")));

            CPClient niceCheck = new CPClient();
            int iReturn = niceCheck.fnDecode(siteCode, sitePassword, encodeData);
            if (iReturn != 0) {
                throw new RuntimeException("NICE 복호화 실패: " + iReturn);
            }

            String plain = niceCheck.getPlainData();
            log.info("NICE 복호화 결과 전체: [{}]", plain);

            // NICE 공식 샘플과 동일하게 fnParse 사용
            @SuppressWarnings("unchecked")
            java.util.HashMap<String, String> mapResult = niceCheck.fnParse(plain);

            log.info("NICE fnParse keys: {}", mapResult.keySet());
            log.info("NICE fnParse 전체: {}", mapResult);

            // UTF8_NAME은 URL-인코딩된 UTF-8 문자열 (예: %EC%9D%B4...) → 디코딩 필요
            String name = "";
            String utf8Name = mapResult.getOrDefault("UTF8_NAME", "");
            if (!utf8Name.isEmpty()) {
                try {
                    name = java.net.URLDecoder.decode(utf8Name, java.nio.charset.StandardCharsets.UTF_8.name());
                    log.info("NICE UTF8_NAME decoded: [{}]", name);
                } catch (Exception e) {
                    log.warn("NICE UTF8_NAME 디코딩 실패, raw 사용: {}", e.getMessage());
                    name = utf8Name;
                }
            }
            if (name.isEmpty()) name = mapResult.getOrDefault("NAME", "");

            NiceAuthResult result = NiceAuthResult.builder()
                    .success(true)
                    .requestNo(mapResult.getOrDefault("REQ_SEQ", ""))
                    .name(name)
                    .birthDate(mapResult.getOrDefault("BIRTHDATE", ""))
                    .gender(mapResult.getOrDefault("GENDER", ""))
                    .mobileNo(mapResult.getOrDefault("MOBILE_NO", ""))
                    .di(mapResult.getOrDefault("DI", ""))
                    .ci(mapResult.getOrDefault("CI", ""))
                    .build();

            if (!result.getRequestNo().isEmpty()) {
                String key = REDIS_PREFIX + "result:" + result.getRequestNo();
                redisTemplate.opsForValue().set(key, toJson(result), RESULT_TTL);
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
        redisTemplate.delete(key);
        return fromJson(json);
    }

    // ──────────────────────────────────────────────
    // private helpers
    // ──────────────────────────────────────────────
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
