package co.kr.mmsoft.mmmemberservice.controller;

import co.kr.mmsoft.mmmemberservice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 엠엠소프트 KSPay V1.4 결제 콜백 처리
 *
 * V1.4 흐름:
 *   index.html → _pay() → KSNET iframe → POST sndReply
 *   POST /api/payment/kspayrec  ← nginx /manyman/KSPayRcv.asp
 *     → window.parent.postMessage(KSPAY_RCV) → index.html → form submit
 *   POST /api/payment/kspayresult  ← nginx /manyman/KSPayResult.asp
 *     → KSNET WebHost 검증 → DB 처리 → window.opener.postMessage(KSPAY_RESULT)
 *
 * ※ JWT 인증 없이 호출됨 → JwtFilter.PUBLIC_PREFIX 에 "/api/payment/" 필요
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private static final String KSNET_WEBHOST_URL =
            "http://kspay.ksnet.to/store/KSPayWebV1.4/web_host/recv_post.jsp";
    private static final String KSNET_MOBILE_WEBHOST_URL =
            "http://kspay.ksnet.to/store/KSPayMobileV1.4/web_host/recv_post.jsp";
    private static final String KSNET_RPARAMS =
            "authyn`trno`trddt`trdtm`amt`authno`msg1`msg2`ordno`isscd`aqucd`result`resultcd";

    private final Optional<PaymentService> paymentService;

    @Autowired
    public PaymentController(Optional<PaymentService> paymentService) {
        this.paymentService = paymentService;
    }

    // -----------------------------------------------------------------------
    // V1.4: KSPayRcv.asp 대체
    //   KSNET iframe → POST reCommConId/reCommType/reHash/reCnclType
    //   → window.parent.postMessage(KSPAY_RCV) → index.html
    // -----------------------------------------------------------------------
    @PostMapping(value = "/kspayrec", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> kspayrec(HttpServletRequest req) {

        String rcid = param(req, "reCommConId");

        if (!rcid.isEmpty()) {
            // ── V1.4 흐름 ──
            String rctype   = param(req, "reCommType");
            String rhash    = param(req, "reHash");
            String cnclType = param(req, "reCnclType");
            boolean cancel  = "1".equals(cnclType);
            log.info("KSPayRcv V1.4 - rcid={}, cancel={}", rcid, cancel);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildKsPayRcvV14(cancel, rcid, rctype, rhash));
        }

        // ── V1.3 레거시 흐름 ──
        String id = req.getParameter("id");
        if (id == null) id = "";

        String approvalType = param(req, "reApprovalType");
        String authyn, amt, msg1, msg2, ordno;
        char type = approvalType.isEmpty() ? '0' : approvalType.charAt(0);

        if (type == '1' || type == 'I') {
            authyn = param(req, "reStatus");  amt = param(req, "reAmount");
            msg1   = param(req, "reMessage1"); msg2 = param(req, "reMessage2");
            ordno  = param(req, "reOrderNumber");
        } else if (type == '4') {
            authyn = param(req, "rePStatus"); amt = param(req, "reAmount");
            msg1   = param(req, "rePMessage1"); msg2 = param(req, "rePMessage2");
            ordno  = param(req, "reOrderNumber");
        } else if (type == '6' || type == '2') {
            authyn = param(req, "reVAStatus"); amt = param(req, "reAmount");
            msg1   = param(req, "reVAMessage1"); msg2 = param(req, "reVAMessage2");
            ordno  = param(req, "reOrderNumber");
        } else {
            authyn = ""; amt = ""; msg1 = ""; msg2 = ""; ordno = "";
        }

        log.info("KSPayRcv V1.3 - authyn={}, amt={}, ordno={}, id={}", authyn, amt, ordno, id);
        boolean success = "O".equalsIgnoreCase(authyn);
        String formatted = "";
        if (success) {
            try {
                int price = Integer.parseInt(amt);
                formatted = String.format("%,d", price);
                String finalId = id;
                paymentService.ifPresentOrElse(
                        svc -> svc.processPaymentSuccess(finalId, price),
                        ()  -> log.warn("PaymentService 미설정"));
            } catch (Exception e) {
                log.error("V1.3 결제 처리 오류", e);
                success = false;
            }
        }
        String msg = msg1 + (msg2.isEmpty() ? "" : " " + msg2);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildKsPayRcvV13(success, formatted, msg));
    }

    // -----------------------------------------------------------------------
    // V1.4: KSPayResult.asp 대체
    //   index.html form submit → reCommConId/reCommType/reHash/sndAmount/a/b
    //   → KSNET WebHost 검증 → DB 처리 → window.opener.postMessage(KSPAY_RESULT)
    // -----------------------------------------------------------------------
    @PostMapping(value = "/kspayresult", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> kspayresult(HttpServletRequest req) {

        String rcid    = param(req, "reCommConId");
        String amount  = param(req, "sndAmount");
        String uid     = param(req, "a");
        String pamount = param(req, "b");
        // apiflg: URL QueryString(?apiflg=Y) 또는 form field d 에서 읽음
        String apiFlg  = param(req, "apiflg");
        if (apiFlg.isEmpty()) apiFlg = param(req, "d");
        boolean isApi  = "Y".equalsIgnoreCase(apiFlg);

        log.info("KSPayResult - rcid={}, amount={}, uid={}, isApi={}", rcid, amount, uid, isApi);

        String authyn = "X", amt = "", msg1 = "";

        try {
            Map<String, String> ksnet = callKsnetWebHost(rcid, amount);
            authyn = ksnet.getOrDefault("authyn", "X");
            amt    = ksnet.getOrDefault("amt",    "");
            msg1   = ksnet.getOrDefault("msg1",   "");
            log.info("KSNET 검증 결과 - authyn={}, amt={}, msg={}", authyn, amt, msg1);
        } catch (Exception e) {
            log.error("KSNET WebHost 호출 실패", e);
            msg1 = "결제 검증 오류";
        }

        boolean success = "O".equalsIgnoreCase(authyn);

        if (success && !uid.isEmpty() && !pamount.isEmpty()) {
            try {
                int price = Integer.parseInt(pamount);
                if (isApi) {
                    // API 충전: M_sms "카드충전-API" 기록만, manyman.payment 미업데이트
                    paymentService.ifPresentOrElse(
                            svc -> svc.recordApiCharge(uid, price),
                            ()  -> log.warn("PaymentService 미설정"));
                } else {
                    // 일반 충전: manyman.payment 업데이트 + M_sms "카드충전" + SMS
                    paymentService.ifPresentOrElse(
                            svc -> svc.processPaymentSuccess(uid, price),
                            ()  -> log.warn("PaymentService 미설정 - mssql-manyman 설정을 확인하세요"));
                }
            } catch (NumberFormatException e) {
                log.error("결제금액 파싱 오류 - pamount={}", pamount);
                success = false;
                msg1 = "결제금액 오류";
            } catch (Exception e) {
                log.error("결제 DB 처리 오류 - uid={}", uid, e);
                success = false;
                msg1 = "DB 처리 오류";
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildKspayResult(success, amt, msg1, isApi));
    }

    // -----------------------------------------------------------------------
    // V1.4 Mobile: KSPayMobileRcv.asp 대체
    //   KSPay mobile → POST sndReply (?uid=&pamount=&apiflg=)
    //   → KSNET Mobile WebHost 검증 → DB 처리 → 결과 HTML (React /payment 로 리다이렉트)
    // -----------------------------------------------------------------------
    @PostMapping(value = "/kspayresult-mobile", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> kspayresultMobile(HttpServletRequest req) {

        // KSPay 모바일 게이트웨이는 결제 검증을 마치고 결과를 POST body에 직접 전달
        // reCommConId 방식의 KSNET WebHost 재검증은 모바일에서 사용하지 않음
        String cnclType = param(req, "reCnclType");
        String uid     = param(req, "uid");
        String pamount = param(req, "pamount");
        String apiFlg  = param(req, "apiflg");
        boolean isApi  = "Y".equalsIgnoreCase(apiFlg);

        // KSPay 모바일 POST body에서 직접 결과 수신
        String authyn = param(req, "authyn");
        String amt    = param(req, "amt");
        String msg1   = param(req, "msg1");
        if (amt.isEmpty()) amt = pamount;

        log.info("KSPayResult Mobile - authyn={}, amt={}, uid={}, isApi={}, cnclType={}",
                authyn, amt, uid, isApi, cnclType);

        if ("1".equals(cnclType)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildMobileResult(false, "", "결제가 취소되었습니다.", isApi, 0, uid));
        }

        boolean success = "O".equalsIgnoreCase(authyn);
        int chargeAmt = 0;

        if (success && !uid.isEmpty() && !pamount.isEmpty()) {
            try {
                int price = Integer.parseInt(pamount);
                if (isApi) {
                    chargeAmt = (int) Math.floor(price / 11.0 * 10);
                    paymentService.ifPresentOrElse(
                            svc -> svc.recordApiCharge(uid, price),
                            ()  -> log.warn("PaymentService 미설정"));
                    // 모바일은 클라이언트로 postMessage 불가 → 서버에서 직접 잔액 충전
                    if (chargeAmt > 0) callNoimCardCharge(uid, chargeAmt);
                } else {
                    paymentService.ifPresentOrElse(
                            svc -> svc.processPaymentSuccess(uid, price),
                            ()  -> log.warn("PaymentService 미설정"));
                }
            } catch (Exception e) {
                log.error("Mobile 결제 DB 처리 오류 - uid={}", uid, e);
                success = false;
                msg1 = "DB 처리 오류";
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildMobileResult(success, amt, msg1, isApi, chargeAmt, uid));
    }

    // -----------------------------------------------------------------------
    // V1.3 result.asp 대체 (레거시)
    // -----------------------------------------------------------------------
    @PostMapping(value = "/result", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> result(HttpServletRequest req) {

        String authyn   = param(req, "reAuthyn");
        String id       = param(req, "a");
        String priceStr = param(req, "b");
        String msg1     = param(req, "reMsg1");
        String trno     = param(req, "reTrno");

        log.info("결제 결과(V1.3) - authyn={}, id={}, price={}, trno={}", authyn, id, priceStr, trno);
        boolean success = "O".equalsIgnoreCase(authyn);

        if (success) {
            try {
                int price = Integer.parseInt(priceStr);
                paymentService.ifPresentOrElse(
                        svc -> svc.processPaymentSuccess(id, price),
                        ()  -> log.warn("PaymentService 미설정"));
            } catch (Exception e) {
                log.error("V1.3 result 처리 오류", e);
                success = false;
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildResultHtml(success, id, priceStr, msg1));
    }

    // -----------------------------------------------------------------------
    // KSNET WebHost API 호출 (V1.4 서버 검증) - desktop/mobile 공용
    // -----------------------------------------------------------------------
    private Map<String, String> callKsnetWebHost(String rcid, String amount) throws Exception {
        return callKsnetWebHost(KSNET_WEBHOST_URL, rcid, amount);
    }

    private Map<String, String> callKsnetWebHost(String webHostUrl, String rcid, String amount) throws Exception {
        String query = "sndCommConId=" + URLEncoder.encode(rcid, "UTF-8")
                + "&sndActionType=1"
                + "&sndAmount=" + URLEncoder.encode(amount, "UTF-8")
                + "&sndRpyParams=" + URLEncoder.encode(KSNET_RPARAMS, "UTF-8");

        URL url = new URL(webHostUrl + "?" + query);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(15_000);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "EUC-KR"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }

        String response = sb.toString();
        log.debug("KSNET WebHost 응답: {}", response);

        String[] parts = response.split("`", -1);
        String[] keys  = KSNET_RPARAMS.split("`");
        Map<String, String> result = new HashMap<>();
        // parts[0] = 응답유형 구분자, parts[1..] = 값
        if (parts.length > keys.length) {
            for (int i = 0; i < keys.length; i++) {
                result.put(keys[i], parts[i + 1]);
            }
        }
        return result;
    }

    // -----------------------------------------------------------------------
    // HTML 빌더
    // -----------------------------------------------------------------------

    /** V1.4 KSPayRcv: KSNET iframe → window.parent.postMessage(KSPAY_RCV) → index.html */
    private String buildKsPayRcvV14(boolean cancel, String rcid, String rctype, String rhash) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>"
                + "<script>"
                + "(function(){"
                + "window.parent.postMessage({"
                + "type:'KSPAY_RCV',"
                + "cancel:" + cancel + ","
                + "rcid:"   + esc(rcid)   + ","
                + "rctype:" + esc(rctype) + ","
                + "rhash:"  + esc(rhash)
                + "},'*');"
                + "})();"
                + "</script>"
                + "</body></html>";
    }

    /** V1.4 KSPayResult: window.opener(PaymentPage.jsx)에 결과 postMessage */
    private String buildKspayResult(boolean success, String amt, String msg, boolean isApi) {
        int chargeAmt = 0;
        if (success && isApi && !amt.isEmpty()) {
            try { chargeAmt = (int) Math.floor(Integer.parseInt(amt) / 11.0 * 10); }
            catch (NumberFormatException ignored) {}
        }
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<style>body{font-family:'맑은 고딕',sans-serif;background:#eef2f7;"
                + "display:flex;align-items:center;justify-content:center;height:100vh;margin:0}"
                + ".box{text-align:center;color:#555;font-size:15px}"
                + ".icon{font-size:40px;margin-bottom:12px}</style>"
                + "</head><body>"
                + "<div class=\"box\"><div class=\"icon\" id=\"ico\">⏳</div>"
                + "<p id=\"txt\">결제 결과를 처리하고 있습니다...</p></div>"
                + "<script>"
                + "(function(){"
                + "var ok=" + success + ";"
                + "var amt=" + esc(amt) + ";"
                + "var msg=" + esc(msg) + ";"
                + "if(window.opener&&!window.opener.closed){"
                + "window.opener.postMessage({type:'KSPAY_RESULT',ok:ok,amt:amt,msg:msg,"
                + "isApiCharge:" + isApi + ",chargeAmt:" + chargeAmt + "},'*');"
                + "setTimeout(function(){window.close();},1200);"
                + "}else{"
                + "document.getElementById('ico').textContent=ok?'✅':'❌';"
                + "document.getElementById('txt').innerHTML=ok"
                + "?'결제가 완료되었습니다.<br>'+amt+'원이 처리되었습니다.'"
                + ":'결제가 완료되지 않았습니다.<br>'+(msg||'');"
                + "}"
                + "})();"
                + "</script></body></html>";
    }

    /** V1.3 KSPayRcv: top.opener.paymentResult() 레거시 */
    private String buildKsPayRcvV13(boolean success, String formatted, String msg) {
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"><script>"
                + "function init(){"
                + "try{if(top.opener&&top.opener.paymentResult){"
                + "top.opener.paymentResult(" + success + "," + esc(formatted) + "," + esc(msg) + ");"
                + "}}catch(e){}"
                + "window.close();}"
                + "</script></head>"
                + "<body onload=\"init();\"></body></html>";
    }

    /** V1.3 result.asp: 결과 HTML 페이지 */
    private String buildResultHtml(boolean success, String id, String price, String msg) {
        String color   = success ? "#1a73e8" : "#e53e3e";
        String bgColor = success ? "#e8f0fe" : "#fff5f5";
        String title   = success ? "결제가 완료되었습니다" : "결제가 완료되지 않았습니다";
        String formatted = "";
        if (price != null && !price.isEmpty()) {
            try { formatted = String.format("%,d원", Integer.parseInt(price)); }
            catch (NumberFormatException ignored) {}
        }
        String detail = success
                ? (formatted.isEmpty() ? "" : formatted + " 충전이 정상 처리되었습니다.<br>") + "프로그램을 다시 시작해 주세요."
                : (msg == null || msg.isEmpty() ? "결제가 취소되었거나 오류가 발생했습니다." : msg) + "<br>창을 닫고 다시 시도해 주세요.";
        return "<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\">"
                + "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">"
                + "<title>" + (success ? "결제 완료" : "결제 실패") + "</title>"
                + "<style>body{font-family:'맑은 고딕','Malgun Gothic',sans-serif;background:"
                + bgColor + ";margin:0;padding:30px 20px;text-align:center;}"
                + ".box{background:#fff;border:2px solid " + color + ";border-radius:8px;"
                + "padding:30px 24px;max-width:380px;margin:0 auto;}"
                + ".ttl{font-size:16px;font-weight:bold;color:" + color + ";margin-bottom:14px;}"
                + ".msg{font-size:13px;color:#555;line-height:1.8;margin-bottom:20px;}"
                + ".btn{padding:8px 24px;background:" + color + ";color:#fff;border:none;"
                + "border-radius:4px;font-size:13px;font-weight:bold;cursor:pointer;}"
                + ".cnt{font-size:11px;color:#999;margin-top:10px;}</style></head><body>"
                + "<div class=\"box\"><div class=\"ttl\">" + title + "</div>"
                + "<div class=\"msg\">" + detail + "</div>"
                + "<button class=\"btn\" onclick=\"window.close()\">닫기</button>"
                + "<div class=\"cnt\" id=\"cnt\"></div></div>"
                + "<script>var s=5;function tick(){if(s<=0){window.close();return;}"
                + "document.getElementById('cnt').innerHTML=s+'초 후 자동으로 닫힙니다';s--;setTimeout(tick,1000);}"
                + "tick();</script></body></html>";
    }

    /** V1.4 Mobile 결과: 결과 표시 후 React /payment?mobileResult=1&... 으로 리다이렉트 */
    private String buildMobileResult(boolean success, String amt, String msg, boolean isApi, int chargeAmt, String uid) {
        String encMsg = "", encUid = "", encAmt = "";
        try {
            encMsg = URLEncoder.encode(msg  == null ? "" : msg,  "UTF-8");
            encUid = URLEncoder.encode(uid  == null ? "" : uid,  "UTF-8");
            encAmt = URLEncoder.encode(amt  == null ? "" : amt,  "UTF-8");
        } catch (Exception ignored) {}

        // isApi=N: 서버에서 이미 충전했으므로 클라이언트 이중충전 방지
        String redirectUrl = "/payment?mobileResult=1&ok=" + success
                + "&amt=" + encAmt + "&isApi=N"
                + "&chargeAmt=" + chargeAmt + "&uid=" + encUid + "&msg=" + encMsg;

        String icon  = success ? "✅" : "❌";
        String title = success ? "결제가 완료되었습니다" : "결제가 완료되지 않았습니다";
        String detail;
        if (success) {
            detail = isApi && chargeAmt > 0
                ? "결제금액 " + amt + "원 중 부가세 제외<br>" + chargeAmt + "원이 충전 처리됩니다."
                : amt + "원 결제가 처리되었습니다.";
        } else {
            detail = (msg == null || msg.isEmpty()) ? "결제가 취소되었거나 오류가 발생했습니다." : msg;
        }
        String color = success ? "#1a73e8" : "#e53e3e";

        return "<!DOCTYPE html><html lang=\"ko\">"
                + "<head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + (success ? "결제완료" : "결제실패") + "</title>"
                + "<style>"
                + "body{font-family:'맑은 고딕',sans-serif;background:#eef2f7;"
                + "min-height:100vh;display:flex;align-items:center;justify-content:center;"
                + "margin:0;padding:16px;box-sizing:border-box}"
                + ".box{background:#fff;border-radius:10px;padding:32px 24px;"
                + "text-align:center;max-width:400px;width:100%;"
                + "box-shadow:0 2px 12px rgba(0,0,0,.1)}"
                + ".icon{font-size:48px;margin-bottom:16px}"
                + ".title{font-size:18px;font-weight:bold;margin-bottom:12px;color:" + color + "}"
                + ".detail{font-size:14px;color:#555;line-height:1.8;margin-bottom:24px}"
                + ".btn{display:inline-block;padding:12px 32px;background:" + color + ";"
                + "color:#fff;text-decoration:none;border-radius:6px;"
                + "font-size:15px;font-weight:bold}"
                + ".cnt{font-size:12px;color:#999;margin-top:12px}"
                + "</style></head>"
                + "<body><div class=\"box\">"
                + "<div class=\"icon\">" + icon + "</div>"
                + "<div class=\"title\">" + title + "</div>"
                + "<div class=\"detail\">" + detail + "</div>"
                + "<a class=\"btn\" href=\"" + redirectUrl + "\">홈으로 돌아가기</a>"
                + "<div class=\"cnt\" id=\"cnt\"></div>"
                + "</div>"
                + "<script>"
                + "var s=5,url=" + esc(redirectUrl) + ";"
                + "function tick(){if(s<=0){window.location.href=url;return;}"
                + "document.getElementById('cnt').textContent=s+'초 후 자동으로 이동합니다';s--;setTimeout(tick,1000);}"
                + "tick();"
                + "</script></body></html>";
    }

    /** 모바일 결제 후 noim_sms_balance 충전 — mm-admin-service 직접 호출 */
    private void callNoimCardCharge(String uid, int chargeAmt) {
        try {
            String query = "customerId=" + URLEncoder.encode(uid, "UTF-8")
                    + "&amount=" + chargeAmt
                    + "&secret=mmsoft-internal-key-2025";
            URL url = new URL("http://mm-admin-service:1994/api/noim/sms/card-charge?" + query);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5_000);
            conn.setReadTimeout(5_000);
            conn.connect();
            int status = conn.getResponseCode();
            log.info("noim 잔액 충전 완료(모바일): uid={}, chargeAmt={}, status={}", uid, chargeAmt, status);
            conn.disconnect();
        } catch (Exception e) {
            log.error("noim 잔액 충전 실패(모바일): uid={}, chargeAmt={}", uid, chargeAmt, e);
        }
    }

    /** request.getParameter null-safe */
    private String param(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? "" : v.trim();
    }

    /** JS 문자열 이스케이프 + 따옴표 감싸기 */
    private String esc(String s) {
        if (s == null) s = "";
        s = s.replace("\\", "\\\\")
             .replace("\"", "\\\"")
             .replace("'",  "\\'")
             .replace("\r", "")
             .replace("\n", "");
        return "\"" + s + "\"";
    }
}
