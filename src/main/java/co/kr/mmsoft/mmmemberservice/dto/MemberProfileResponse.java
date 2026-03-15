package co.kr.mmsoft.mmmemberservice.dto;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import lombok.Getter;

/** 회원 프로필 조회 응답 DTO */
@Getter
public class MemberProfileResponse {
    private final Long   accountId;
    private final String openId;
    private final String name;
    private final String email;
    private final String mphone;
    private final String phone;
    private final String company;
    private final String provider;

    public MemberProfileResponse(Account account) {
        this.accountId = account.getAccountId();
        this.openId    = account.getOpenId();
        this.name      = account.getName();
        this.email     = account.getEmail();
        this.mphone    = account.getMphone();
        this.phone     = account.getPhone();
        this.company   = account.getCompany();
        this.provider  = account.getProvider() != null
                         ? account.getProvider().getProviderName() : "local";
    }
}
