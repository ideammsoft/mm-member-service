package co.kr.mmsoft.mmmemberservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {
    private String accessToken;
    private String name;
    private String roleName;
    private Long accountId;

    public LoginResponse(String accessToken, String name, String roleName) {
        this.accessToken = accessToken;
        this.name = name;
        this.roleName = roleName;
    }

    public LoginResponse(String accessToken, String name, String roleName, Long accountId) {
        this.accessToken = accessToken;
        this.name = name;
        this.roleName = roleName;
        this.accountId = accountId;
    }
}
