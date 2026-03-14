package co.kr.mmsoft.mmmemberservice.dto;

import lombok.Getter;
import lombok.Setter;

/** 회원 정보 수정 요청 DTO */
@Getter
@Setter
public class MemberProfileUpdateRequest {
    private Long   accountId; // 서버에서 JWT로 세팅 (클라이언트 미입력)
    private String name;
    private String email;
    private String phone;
    private String company;
}
