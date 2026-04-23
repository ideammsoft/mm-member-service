package co.kr.mmsoft.mmmemberservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    /**
     * 임시 비밀번호 발송
     * @param to        수신자 이메일
     * @param tempPassword 임시 비밀번호 (평문)
     */
    public void sendContactInquiry(String type, String name, String region, String phone) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("ideakslee@gmail.com");
            message.setTo("man@mmsoft.co.kr");
            message.setSubject("[MM기사모집] 문의 접수 - " + type);
            message.setText(
                "광고 페이지 문의가 접수되었습니다.\n\n" +
                "구분: " + type + "\n" +
                "성명: " + name + "\n" +
                "지역: " + region + "\n" +
                "연락처: " + phone + "\n\n" +
                "담당자 확인 후 연락해 주세요."
            );
            mailSender.send(message);
            log.debug("문의 이메일 발송 완료 - type: {}, name: {}", type, name);
        } catch (Exception e) {
            log.error("문의 이메일 발송 실패 - error: {}", e.getMessage());
            throw new RuntimeException("이메일 발송에 실패했습니다.");
        }
    }

    public void sendTempPassword(String to, String tempPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("ideakslee@gmail.com");
            message.setTo(to);
            message.setSubject("[엠엠소프트] 임시 비밀번호 안내");
            message.setText(
                "안녕하세요. 엠엠소프트입니다.\n\n" +
                "요청하신 임시 비밀번호를 안내드립니다.\n\n" +
                "임시 비밀번호: " + tempPassword + "\n\n" +
                "로그인 후 반드시 비밀번호를 변경해 주세요.\n\n" +
                "감사합니다."
            );
            mailSender.send(message);
            log.debug("임시 비밀번호 이메일 발송 완료 → {}", to);
        } catch (Exception e) {
            log.error("이메일 발송 실패 - to: {}, error: {}", to, e.getMessage());
            throw new RuntimeException("이메일 발송에 실패했습니다.");
        }
    }
}
