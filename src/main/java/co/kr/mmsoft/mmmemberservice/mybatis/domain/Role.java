package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Role {
    private int roleId;
    private String roleName;

    //해당 롤이 보유한 권한들..
    private List<Permission> permissionList;
}
