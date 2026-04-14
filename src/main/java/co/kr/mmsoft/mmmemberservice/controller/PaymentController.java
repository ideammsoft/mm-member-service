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

import java.util.Optional;

/**
 * 엠엠소프트 KSPay 결제 콜백 처리
 *
 * POST /api/payment/kspayrec  ← nginx가 /manyman/KSPayRcv.asp 를 프록시
 *   - KSPay 팝업 창에서 결제 완료 후 호출
 *   - 결과 파라미터를 부모 창(authfrm.html)의 paramSet() → goResult() 로 전달하는 HTML 응답
 *
 * POST /api/payment/result  ← nginx가 /manyman/result.asp 를 프록시
 *   - authfrm.html의 KSPayWeb form이 submit 한 결과 수신
 *   - 결제 성공 시 MSSQL (manyman.payment 증가, M_sms INSERT, SMS 발송)
 *   - 성공/실패 HTML 페이지 반환
 *
 * ※ JWT 인증 없이 호출됨 (PC 앱이 직접 열기, KSPay 팝업 콜백)
 *    → JwtFilter.PUBLIC_PREFIX 에 "/api/payment/" 추가 필요
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final Optional<PaymentService> paymentService;

    @Autowired
    public PaymentController(Optional<PaymentService> paymentService) {
        this.paymentService = paymentService;
    }

    // -----------------------------------------------------------------------
    // KSPayRcv.asp 대체: KSPay 결제 완료 → 직접 DB 처리 → 팝업에 결과 HTML 반환
    // sndReply URL 에 ?id=userId 포함하여 호출됨
    // -----------------------------------------------------------------------
    @PostMapping(value = "/kspayrec", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> kspayrec(HttpServletRequest req) {

        // sndReply URL 쿼리 파라미터에서 사용자 ID 추출
        String id = req.getParameter("id");
        if (id == null) id = "";

        // KSPay 전문 유형별 결과 파라미터 파싱 (원본 KSPayRcv.asp 로직 동일)
        String approvalType = param(req, "reApprovalType");
        String authyn, amt, msg1, msg2, ordno;

        char type = approvalType.isEmpty() ? '0' : approvalType.charAt(0);

        if (type == '1' || type == 'I') {        // 신용카드 (MPI / ISP)
            authyn = param(req, "reStatus");
            amt    = param(req, "reAmount");
            msg1   = param(req, "reMessage1");
            msg2   = param(req, "reMessage2");
            ordno  = param(req, "reOrderNumber");
        } else if (type == '4') {                // 포인트
            authyn = param(req, "rePStatus");
            amt    = param(req, "reAmount");
            msg1   = param(req, "rePMessage1");
            msg2   = param(req, "rePMessage2");
            ordno  = param(req, "reOrderNumber");
        } else if (type == '6' || type == '2') { // 가상계좌 / 계좌이체
            authyn = param(req, "reVAStatus");
            amt    = param(req, "reAmount");
            msg1   = param(req, "reVAMessage1");
            msg2   = param(req, "reVAMessage2");
            ordno  = param(req, "reOrderNumber");
        } else if (type == '7') {               // 월드패스
            authyn = param(req, "reWPStatus");
            amt    = param(req, "reAmount");
            msg1   = param(req, "reWPMessage1");
            msg2   = param(req, "reWPMessage2");
            ordno  = param(req, "reOrderNumber");
        } else {
            authyn = ""; amt = ""; msg1 = ""; msg2 = ""; ordno = "";
        }

        log.info("KSPayRcv - authyn={}, amt={}, ordno={}, id={}", authyn, amt, ordno, id);

        boolean success = "O".equalsIgnoreCase(authyn);
        String formatted = "";

        if (success) {
            try {
                int price = Integer.parseInt(amt);
                try {
                    formatted = String.format("%,d", price);
                } catch (Exception ignored) {}
                String finalId = id;
                paymentService.ifPresentOrElse(
                        svc -> svc.processPaymentSuccess(finalId, price),
                        ()  -> log.warn("PaymentService 미설정 - mssql-manyman 설정을 확인하세요")
                );
            } catch (NumberFormatException e) {
                log.error("결제금액 파싱 오류 - amt={}", amt);
                success = false;
            } catch (Exception e) {
                log.error("결제 DB 처리 중 오류 - id={}", id, e);
                success = false;
            }
        } else {
            log.warn("결제 실패 응답 - authyn={}, msg={}", authyn, msg1);
        }

        String msg = msg1 + (msg2.isEmpty() ? "" : " " + msg2);

        // 팝업 내부 프레임에서 실행: 부모창(authfrm.html)에 결과 통보 후 팝업 닫기
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildKsPayRcvJs(success, formatted, msg));
    }

    // -----------------------------------------------------------------------
    // result.asp 대체: KSPayWeb form submit → MSSQL 저장 → 결과 HTML
    // -----------------------------------------------------------------------
    @PostMapping(value = "/result", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> result(HttpServletRequest req) {

        String authyn = param(req, "reAuthyn");
        String id     = param(req, "a");      // 사용자 아이디
        String priceStr = param(req, "b");    // 충전 금액
        String msg1   = param(req, "reMsg1");
        String trno   = param(req, "reTrno");

        log.info("결제 결과 수신 - authyn={}, id={}, price={}, trno={}", authyn, id, priceStr, trno);

        boolean success = "O".equalsIgnoreCase(authyn);

        if (success) {
            try {
                int price = Integer.parseInt(priceStr);
                paymentService.ifPresentOrElse(
                        svc -> svc.processPaymentSuccess(id, price),
                        ()  -> log.warn("PaymentService 미설정 - mssql-manyman 설정을 확인하세요")
                );
            } catch (NumberFormatException e) {
                log.error("결제금액 파싱 오류 - priceStr={}", priceStr);
                success = false;
            } catch (Exception e) {
                log.error("결제 DB 처리 중 오류 - id={}", id, e);
                success = false;
            }
        } else {
            log.warn("결제 실패 응답 - authyn={}, msg={}", authyn, msg1);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(buildResultHtml(success, id, priceStr, msg1));
    }

    // -----------------------------------------------------------------------
    // 헬퍼
    // -----------------------------------------------------------------------

    /** request.getParameter null-safe */
    private String param(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        return v == null ? "" : v.trim();
    }

    /**
     * KSPay 팝업 내부 프레임에서 실행되는 JS:
     *   1) top.opener.paymentResult() → authfrm.html에 결과 통보
     *   2) window.close() → 팝업 전체 닫기 (IE: iframe에서 호출 시 top window 닫힘)
     */
    private String buildKsPayRcvJs(boolean success, String formatted, String msg) {
        String okStr  = success ? "true" : "false";
        String amtEsc = esc(formatted);
        String msgEsc = esc(msg);
        return "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">"
                + "<script>"
                + "function init(){"
                + "try{"
                + "if(top.opener&&top.opener.paymentResult){"
                + "top.opener.paymentResult(" + okStr + "," + amtEsc + "," + msgEsc + ");"
                + "}"
                + "}catch(e){}"
                + "window.close();"
                + "}"
                + "</script>"
                + "</head>"
                + "<body onload=\"init();\"></body></html>";
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

    /** 결제 결과 HTML 페이지 (IE 호환) */
    private String buildResultHtml(boolean success, String id, String price, String msg) {
        String color     = success ? "#1a73e8" : "#e53e3e";
        String bgColor   = success ? "#e8f0fe" : "#fff5f5";
        String title     = success ? "결제가 완료되었습니다" : "결제가 완료되지 않았습니다";
        String formatted = "";
        if (price != null && !price.isEmpty()) {
            try {
                formatted = String.format("%,d원", Integer.parseInt(price));
            } catch (NumberFormatException ignored) {}
        }
        String detail = success
                ? (formatted.isEmpty() ? "" : formatted + " 충전이 정상 처리되었습니다.<br>")
                  + "프로그램을 다시 시작해 주세요."
                : (msg == null || msg.isEmpty() ? "결제가 취소되었거나 오류가 발생했습니다." : msg)
                  + "<br>창을 닫고 다시 시도해 주세요.";

        return "<!DOCTYPE html><html lang=\"ko\"><head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">"
                + "<title>" + (success ? "결제 완료" : "결제 실패") + "</title>"
                + "<style>"
                + "body{font-family:'맑은 고딕','Malgun Gothic',sans-serif;background:" + bgColor + ";margin:0;padding:30px 20px;text-align:center;}"
                + ".box{background:#fff;border:2px solid " + color + ";border-radius:8px;padding:30px 24px;max-width:380px;margin:0 auto;}"
                + ".ttl{font-size:16px;font-weight:bold;color:" + color + ";margin-bottom:14px;}"
                + ".msg{font-size:13px;color:#555;line-height:1.8;margin-bottom:20px;}"
                + ".btn{padding:8px 24px;background:" + color + ";color:#fff;border:none;border-radius:4px;font-size:13px;"
                + "font-family:'맑은 고딕','Malgun Gothic',sans-serif;font-weight:bold;cursor:pointer;}"
                + ".cnt{font-size:11px;color:#999;margin-top:10px;}"
                + "</style></head><body>"
                + "<div class=\"box\">"
                + "<div class=\"ttl\">" + title + "</div>"
                + "<div class=\"msg\">" + detail + "</div>"
                + "<button class=\"btn\" onclick=\"window.close()\">닫기</button>"
                + "<div class=\"cnt\" id=\"cnt\"></div>"
                + "</div>"
                + "<script>"
                + "var s=5;"
                + "function tick(){if(s<=0){window.close();return;}"
                + "document.getElementById('cnt').innerHTML=s+'초 후 자동으로 닫힙니다';s--;setTimeout(tick,1000);}"
                + "tick();"
                + "</script>"
                + "</body></html>";
    }
}
