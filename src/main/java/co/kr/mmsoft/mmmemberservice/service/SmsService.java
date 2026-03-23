package co.kr.mmsoft.mmmemberservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * SMS 전송 서비스 - manymen_ppurio.ums_data 테이블에 INSERT
 * 비밀번호 찾기(휴대폰 방식) 시 임시 비밀번호를 문자로 발송
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.mssql-ppurio", name = "jdbc-url")
public class SmsService {

    private static final String SEND_PHONE = "028647576"; // 발신 번호

    private final DataSource ppurioDataSource;

    public SmsService(@Qualifier("mssqlPpurioDataSource") DataSource ppurioDataSource) {
        this.ppurioDataSource = ppurioDataSource;
    }

    /**
     * 임시 비밀번호 SMS 전송
     *
     * @param destPhone   수신 휴대폰 번호 (하이픈 포함 가능, 자동 제거)
     * @param tempPassword 임시 비밀번호 (평문)
     */
    public void sendTempPassword(String destPhone, String tempPassword) {
        if (destPhone == null || destPhone.isBlank()) {
            log.warn("SMS 발송 스킵 - 수신 번호 없음");
            return;
        }

        String cleanPhone = destPhone.replaceAll("[^0-9]", "");
        if (cleanPhone.isEmpty()) {
            log.warn("SMS 발송 스킵 - 유효한 전화번호 없음: {}", destPhone);
            return;
        }

        String now     = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String cmid    = now + String.format("%06d", new Random().nextInt(1_000_000));
        String msgBody = "[엠엠소프트] 임시 비밀번호: " + tempPassword
                + " 로그인 후 반드시 비밀번호를 변경해 주세요.";

        String sql = """
                INSERT INTO ums_data
                    (cmid, msg_type, status, request_time, dest_phone, send_phone, msg_body, etc1, etc2, etc3, etc4)
                VALUES
                    (?, 0, 0, GETDATE(), ?, ?, ?, '', 'manyman', '', '')
                """;

        try (Connection conn = ppurioDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cmid);
            ps.setString(2, cleanPhone);
            ps.setString(3, SEND_PHONE);
            ps.setString(4, msgBody);
            int rows = ps.executeUpdate();
            log.info("SMS 발송 등록 완료 - cmid: {}, dest: {}, rows: {}", cmid, cleanPhone, rows);
        } catch (Exception e) {
            log.error("SMS 발송 등록 실패 - dest: {}, 오류: {}", cleanPhone, e.getMessage());
            // SMS 실패해도 비밀번호 변경은 완료된 상태이므로 예외 전파 안 함
        }
    }
}
