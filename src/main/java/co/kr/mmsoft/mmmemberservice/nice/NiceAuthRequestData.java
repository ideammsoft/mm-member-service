package co.kr.mmsoft.mmmemberservice.nice;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class NiceAuthRequestData {
    private String formUrl;    // NICE 폼 제출 URL
    private String siteCode;   // 사이트 코드
    private String encData;    // 암호화된 요청 데이터
    private String requestNo;  // 요청 번호 (결과 조회 키)
}
