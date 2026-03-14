package co.kr.mmsoft.mmmemberservice.dto;

import lombok.Getter;

/**
 * OAuth2 임시코드 교환 응답 DTO
 * accessToken + 사용자 기본 정보 포함
 * isNewMember: phone이 비어있으면 true (추가정보 입력 필요)
 */
@Getter
public class OAuthExchangeResponse {
    private final String  accessToken;
    private final String  name;
    private final String  email;
    private final String  phone;
    private final String  company;
    private final String  provider;
    private final boolean isNewMember;

    public OAuthExchangeResponse(String accessToken, String name, String email,
                                  String phone, String company, String provider) {
        this.accessToken = accessToken;
        this.name        = name;
        this.email       = email;
        this.phone       = phone;
        this.company     = company;
        this.provider    = provider;
        this.isNewMember = (phone == null || phone.isBlank());
    }
}
