package co.kr.mmsoft.mmmemberservice.dto;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Provider;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegistRequest {
    //DTO는 파라미터를 받거나, 응답정보로 사용하는 용도이므로
    //굳이 Database 컬럼명과 일치시킬 필요가 없다(그럼 누가 일치해야 하나? Entity, Domain)
    @JsonProperty("openId")
    private String openId;
    private String password;
    private String name;
    private String email;
    private String mphone;
    private String phone;
    private String company;
    private Provider provider;
}
