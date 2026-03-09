package co.kr.mmsoft.mmmemberservice.dto;

import lombok.Getter;
import lombok.Setter;

//AT와 RT정보를 담아서 Controller에게 전달
@Getter
@Setter

public class AuthTokens {
    private String accessToken;
    private String refreshToken;

    public AuthTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}
