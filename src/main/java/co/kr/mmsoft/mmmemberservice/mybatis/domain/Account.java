package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor //mybatis가 내부적으로 Domain객체를 생성하게 되므로 반드시 선언
public class Account {
    private Long accountId;
    private String openId;
    private String password;
    private String name;
    private String email;
    private String phone;
    private String company;

    private Provider provider; //1건은 association

    private List<Role> roleList; // 1명의 회원은 여러개의 Role을 가질 수 있으므로...

    //Builder 패턴은 필수는 아니다.. 단지 개발자가 객체 생성시 생성자의 매개변수를 혼동할 수 있는 실수를 막아줌
    @Builder
    public Account(Long accountId, String openId, String password, String name, String email, String phone, String company, Provider provider ){
        this.accountId=accountId;
        this.openId=openId;
        this.password=password;
        this.name=name;
        this.email=email;
        this.phone=phone;
        this.company=company;
        this.provider=provider;
    }
}
