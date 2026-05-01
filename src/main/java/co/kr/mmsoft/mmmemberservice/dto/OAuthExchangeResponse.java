package co.kr.mmsoft.mmmemberservice.dto;

import lombok.Getter;

/**
 * OAuth2 임시코드 교환 응답 DTO
 * accessToken + 사용자 기본 정보 포함
 * isNewMember: mphone이 비어있으면 true (추가정보 입력 필요)
 */
@Getter
public class OAuthExchangeResponse {
    private final String  accessToken;
    private final String  name;
    private final String  email;
    private final String  mphone;
    private final String  phone;
    private final String  company;
    private final String  provider;
    private final String  homepageId;
    private final boolean isNewMember;

    public OAuthExchangeResponse(String accessToken, String name, String email,
                                  String mphone, String phone, String company, String provider, String homepageId) {
        this.accessToken = accessToken;
        this.name        = name;
        this.email       = email;
        this.mphone      = mphone;
        this.phone       = phone;
        this.company     = company;
        this.provider    = provider;
        this.homepageId  = homepageId;
        this.isNewMember = (mphone == null || mphone.isBlank());
    }
}
