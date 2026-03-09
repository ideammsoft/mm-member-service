package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountRole {
    private int accountRoleId;
    private Account account; //투가?
    private Role role; //어떤 권한을

    public AccountRole(Account account, Role role){
        this.account=account;
        this.role=role;
    }
}
