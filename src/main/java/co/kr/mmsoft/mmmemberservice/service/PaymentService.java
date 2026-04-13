package co.kr.mmsoft.mmmemberservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 엠엠소프트 결제 처리 서비스
 *
 * KSPay 카드결제 완료 후:
 *   1) manyman.payment += 충전금액
 *   2) M_sms 에 충전 이력 INSERT
 *   3) ums_data 에 확인 SMS 발송 요청 INSERT  (ppurio DB)
 *
 * mssql-manyman 설정이 없으면 빈 자체가 생성되지 않음.
 * PaymentController 는 Optional<PaymentService> 로 주입받아 처리함.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.mssql-manyman", name = "jdbc-url")
public class PaymentService {

    private static final String SEND_PHONE = "028647576";   // 엠엠소프트 발신번호

    private final DataSource manymanDs;

    @Autowired(required = false)
    @Qualifier("mssqlPpurioDataSource")
    private DataSource ppurioDs;

    public PaymentService(@Qualifier("mssqlManymanDataSource") DataSource manymanDs) {
        this.manymanDs = manymanDs;
    }

    /**
     * 결제 성공 처리
     *
     * @param id    manyman.id (사용자 아이디)
     * @param price 충전 금액 (원)
     */
    public void processPaymentSuccess(String id, int price) {
        if (id == null || id.isBlank() || price <= 0) {
            log.warn("결제처리 스킵 - id={}, price={}", id, price);
            return;
        }

        try (Connection conn = manymanDs.getConnection()) {
            updatePayment(conn, id, price);
            insertMSms(conn, id, price);
            String mphone = getMphone(conn, id);
            if (mphone != null && mphone.startsWith("01")) {
                sendSmsNotification(id, price, mphone);
            }
        } catch (Exception e) {
            log.error("결제 DB 처리 실패 - id={}, price={}, 오류: {}", id, price, e.getMessage(), e);
            throw new RuntimeException("결제 DB 처리 실패", e);
        }
    }

    /** manyman.payment 잔액 증가 */
    private void updatePayment(Connection conn, String id, int price) throws Exception {
        String sql = "UPDATE manyman SET payment = payment + ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, price);
            ps.setString(2, id);
            int rows = ps.executeUpdate();
            log.info("manyman payment 충전 - id={}, 금액={}, 영향행={}", id, price, rows);
        }
    }

    /** M_sms 충전 이력 등록 */
    private void insertMSms(Connection conn, String id, int price) throws Exception {
        String sql = "INSERT INTO M_sms (id, title, payment) VALUES (?, '카드충전', ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setInt(2, price);
            ps.executeUpdate();
            log.info("M_sms 이력 등록 완료 - id={}", id);
        }
    }

    /** manyman.mphone 조회 */
    private String getMphone(Connection conn, String id) throws Exception {
        String sql = "SELECT mphone FROM manyman WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("mphone");
            }
        }
        return null;
    }

    /** ums_data 에 SMS 발송 요청 (ppurio DB) */
    private void sendSmsNotification(String id, int price, String mphone) {
        if (ppurioDs == null) {
            log.warn("mssql-ppurio DataSource 미설정 - SMS 발송 스킵 (id={})", id);
            return;
        }
        try (Connection conn = ppurioDs.getConnection()) {
            String cmid      = makeCmid();
            String destPhone = mphone.replaceAll("[^0-9]", "");
            String msgBody   = String.format(
                    "문자메시지 %,d원이 충전되었습니다(카드결제). - 엠엠소프트", price);

            String sql = "INSERT INTO ums_data" +
                    " (cmid, msg_type, status, request_time, dest_phone, send_phone, msg_body, etc1, etc2, etc3, etc4)" +
                    " VALUES (?, '0', '0', GETDATE(), ?, ?, ?, '', 'manyman', '', '')";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, cmid);
                ps.setString(2, destPhone);
                ps.setString(3, SEND_PHONE);
                ps.setString(4, msgBody);
                ps.executeUpdate();
                log.info("SMS 발송 요청 등록 - id={}, mphone={}", id, mphone);
            }
        } catch (Exception e) {
            log.warn("SMS 발송 요청 실패 - id={}, 오류: {}", id, e.getMessage());
            // SMS 실패는 결제 성공에 영향 없음 (warn 후 계속)
        }
    }

    /** CMID 생성: 날짜시간(17자리) + 랜덤(7자리) = 24자리 */
    private String makeCmid() {
        LocalDateTime now = LocalDateTime.now();
        String ts  = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        String rnd = String.format("%07d",
                ThreadLocalRandom.current().nextLong(0, 9_999_999L));
        return ts + rnd;
    }
}
