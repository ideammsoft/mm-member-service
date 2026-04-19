package co.kr.mmsoft.mmmemberservice.controller;

import co.kr.mmsoft.mmmemberservice.nice.NiceAuthRequestData;
import co.kr.mmsoft.mmmemberservice.nice.NiceAuthResult;
import co.kr.mmsoft.mmmemberservice.nice.NiceAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * NICE 본인인증 (구형 CheckPlus v1) API
 *
 * [흐름]
 *   GET  /api/auth/nice/request          → 암호화 요청 데이터 반환 (프론트 → 팝업 오픈)
 *   POST /api/auth/nice/success          → NICE 서버 → 우리 서버 성공 콜백 (HTML 반환)
 *   POST /api/auth/nice/fail             → NICE 서버 → 우리 서버 실패 콜백 (HTML 반환)
 *   GET  /api/auth/nice/result/{reqNo}   → 프론트가 결과 조회 (requestNo 기반)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/nice")
@RequiredArgsConstructor
public class NiceAuthController {

    private final NiceAuthService niceAuthService;

    /**
     * 1단계: 인증 요청 데이터 생성
     * 프론트에서 팝업 폼에 넣을 encData, siteCode, formUrl 반환
     */
    @GetMapping("/request")
    public ResponseEntity<NiceAuthRequestData> request(
            @RequestParam(defaultValue = "M") String authType) {
        NiceAuthRequestData data = niceAuthService.buildRequest(authType);
        return ResponseEntity.ok(data);
    }

    /**
     * 2단계: NICE 성공 콜백 (NICE 서버가 POST로 호출)
     * - EncodeData: 암호화된 인증 결과
     * - 복호화 후 Redis 저장 → 팝업 닫는 HTML 반환
     */
    @RequestMapping(value = "/success", method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaType.TEXT_HTML_VALUE)
    public String success(@RequestParam("EncodeData") String encodeData) {
        NiceAuthResult result = niceAuthService.processSuccess(encodeData);
        log.info("NICE 인증 성공: name={}, mobile={}", result.getName(), result.getMobileNo());
        return buildCompleteHtml(result);
    }

    /**
     * 3단계: NICE 실패 콜백
     */
    @RequestMapping(value = "/fail", method = {RequestMethod.POST, RequestMethod.GET}, produces = MediaType.TEXT_HTML_VALUE)
    public String fail(@RequestParam(value = "EncodeData", required = false) String encodeData) {
        log.warn("NICE 인증 실패 콜백 수신");
        NiceAuthResult result = NiceAuthResult.builder()
                .success(false).errorMsg("본인인증에 실패했습니다.").build();
        return buildCompleteHtml(result);
    }

    /**
     * 4단계: 프론트에서 requestNo로 결과 조회
     * 팝업이 닫힌 후 부모 창에서 호출
     */
    @GetMapping("/result/{requestNo}")
    public ResponseEntity<?> result(@PathVariable String requestNo) {
        NiceAuthResult r = niceAuthService.getResult(requestNo);
        if (r == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "success",   r.isSuccess(),
                "name",      r.getName() != null ? r.getName() : "",
                "birthDate", r.getBirthDate() != null ? r.getBirthDate() : "",
                "gender",    r.getGender() != null ? r.getGender() : "",
                "mobileNo",  r.getMobileNo() != null ? r.getMobileNo() : "",
                "di",        r.getDi() != null ? r.getDi() : ""
        ));
    }

    // ──────────────────────────────────────────────
    // NICE 콜백 후 팝업 닫는 HTML 생성
    // ──────────────────────────────────────────────
    private String buildCompleteHtml(NiceAuthResult result) {
        String data;
        if (result.isSuccess()) {
            data = String.format(
                "{success:true,requestNo:'%s',name:'%s',birthDate:'%s',gender:'%s',mobileNo:'%s',di:'%s'}",
                esc(result.getRequestNo()), esc(result.getName()),
                esc(result.getBirthDate()), esc(result.getGender()),
                esc(result.getMobileNo()), esc(result.getDi())
            );
        } else {
            data = String.format(
                "{success:false,errorMsg:'%s'}",
                esc(result.getErrorMsg())
            );
        }

        // postMessage 방식: 크로스 오리진 안전, 부모창이 popup.close() 호출
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'>" +
               "<meta http-equiv='X-UA-Compatible' content='IE=edge'>" +
               "</head><body>" +
               "<div style='text-align:center;margin-top:80px;color:#555;font-family:sans-serif'>" +
               "<p>인증이 완료되었습니다.</p><p style='font-size:12px'>잠시 후 자동으로 닫힙니다...</p>" +
               "</div>" +
               "<script>" +
               "var d=" + data + ";" +
               "try{" +
               "  if(window.opener){" +
               "    window.opener.postMessage({type:'niceAuthComplete',data:d},'*');" +
               "  }" +
               "}catch(e){console.error(e);}" +
               "setTimeout(function(){try{window.close();}catch(e){}},800);" +
               "</script>" +
               "</body></html>";
    }

    private String esc(String s) { return s == null ? "" : s.replace("'", "\\'"); }
}
