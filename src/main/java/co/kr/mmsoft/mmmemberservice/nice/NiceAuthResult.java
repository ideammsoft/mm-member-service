package co.kr.mmsoft.mmmemberservice.nice;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NiceAuthResult {
    private boolean success;
    private String requestNo;
    private String name;       // 성명 (UTF-8)
    private String birthDate;  // 생년월일 (YYYYMMDD)
    private String gender;     // 성별 (0:여 1:남)
    private String mobileNo;   // 휴대폰 번호
    private String di;         // 중복가입 확인정보 (64자)
    private String ci;         // 연계정보 (88자)
    private String errorMsg;   // 실패 시 오류 메시지
}
