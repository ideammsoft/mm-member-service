package co.kr.mmsoft.mmmemberservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginResponse {
    private String accessToken;
    private String name;
    private String email;
    private String roleName;
    private Long accountId;
    private String expiryDate;

    public LoginResponse(String accessToken, String name, String roleName) {
        this.accessToken = accessToken;
        this.name = name;
        this.roleName = roleName;
    }

    public LoginResponse(String accessToken, String name, String email, String roleName, Long accountId) {
        this.accessToken = accessToken;
        this.name = name;
        this.email = email;
        this.roleName = roleName;
        this.accountId = accountId;
    }

    public LoginResponse(String accessToken, String name, String email, String roleName, Long accountId, String expiryDate) {
        this.accessToken = accessToken;
        this.name = name;
        this.email = email;
        this.roleName = roleName;
        this.accountId = accountId;
        this.expiryDate = expiryDate;
    }
}
