package co.kr.mmsoft.mmmemberservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Set;

/**
 * 080 ARS 수신거부 콜백 API
 * Ppurio ARS 서버 → POST/GET /api/blacklist/ars-reject?PhoneNumber=01012341234
 */
@Slf4j
@RestController
@ConditionalOnProperty(prefix = "app.mssql-ppurio", name = "jdbc-url")
public class BlackListController {

    private static final Set<String> ALLOWED_IPS = Set.of("222.239.78.108", "222.122.194.194");

    private final DataSource ppurioDataSource;

    public BlackListController(@Qualifier("mssqlPpurioDataSource") DataSource ppurioDataSource) {
        this.ppurioDataSource = ppurioDataSource;
    }

    @RequestMapping("/api/blacklist/ars-reject")
    public ResponseEntity<String> arsReject(
            @RequestParam(value = "PhoneNumber", required = false) String phoneNumber,
            HttpServletRequest request) {

        String clientIp = resolveClientIp(request);

        if (!ALLOWED_IPS.contains(clientIp)) {
            log.warn("080 ARS Reject - 허용되지 않은 IP: {}", clientIp);
            return ResponseEntity.status(403).body("FAIL - IP ERROR:" + clientIp);
        }

        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("080 ARS Reject - PhoneNumber 없음");
            return ResponseEntity.badRequest().body("FAIL - PhoneNumber ERROR");
        }

        String cleanPhone = phoneNumber.replaceAll("[^0-9]", "");
        if (cleanPhone.isEmpty()) {
            return ResponseEntity.badRequest().body("FAIL - PhoneNumber ERROR");
        }

        try (Connection conn = ppurioDataSource.getConnection()) {
            try (PreparedStatement del = conn.prepareStatement(
                    "DELETE FROM BlackList WHERE phone = ?")) {
                del.setString(1, cleanPhone);
                del.executeUpdate();
            }
            try (PreparedStatement ins = conn.prepareStatement(
                    "INSERT INTO BlackList (phone, update_flag, update_mode, insert_date) VALUES (?, 1, 'A', GETDATE())")) {
                ins.setString(1, cleanPhone);
                ins.executeUpdate();
            }
            log.info("080 ARS Reject 등록 완료 - phone: {}, ip: {}", cleanPhone, clientIp);
            return ResponseEntity.ok("SUCCESS");
        } catch (Exception e) {
            log.error("080 ARS Reject DB 오류 - phone: {}, 오류: {}", cleanPhone, e.getMessage());
            return ResponseEntity.status(500).body("FAIL - DB ERROR");
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xfwd = request.getHeader("X-Forwarded-For");
        if (xfwd != null && !xfwd.isBlank()) {
            return xfwd.split(",")[0].trim();
        }
        String xrip = request.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) {
            return xrip.trim();
        }
        return request.getRemoteAddr();
    }
}
