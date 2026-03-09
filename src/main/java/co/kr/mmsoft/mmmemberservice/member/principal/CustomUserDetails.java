package co.kr.mmsoft.mmmemberservice.member.principal;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Permission;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Role;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
@Getter
public class CustomUserDetails implements UserDetails {
    private Account account;
    public CustomUserDetails(Account account){
        this.account = account;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        //유저가 보유한 Role, Permission을 담게될 Collection을 준비하자
        List<SimpleGrantedAuthority> authorities= new ArrayList<>();

        /*----------------------------------------------
        이 계정이 보유한 Role  꺼내기
        ----------------------------------------------*/
        for(Role role:account.getRoleList()){
            log.debug("이 계정이 보유한 롤의 이름{}", role.getRoleName());
            //시큐리티에는 Role, Permission을 넣을 수 있다. 이때 Role과 퍼미션의 구분을 롤에 ROLE 반드시 들어가야함
            authorities.add(new SimpleGrantedAuthority("ROLE_"+role.getRoleName())); //ROLE_admin
            /*----------------------------------------------
            이 계정이 보유한 Permission  꺼내기
            ----------------------------------------------*/
            for(Permission permission:role.getPermissionList()){
                log.debug("이 계정의 Role 보유한 퍼미션은 {}", permission.getPermissionName());
                authorities.add(new SimpleGrantedAuthority(permission.getPermissionName()));
            }
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.getPassword();
    }

    //Security에서는 로그인 아이디를 가리켜 username이라고 한다. 따라서 우리의 경우 openId를 말함
    @Override
    public String getUsername() {
        return account.getOpenId();
    }
}
