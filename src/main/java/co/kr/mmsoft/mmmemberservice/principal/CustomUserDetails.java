package co.kr.mmsoft.mmmemberservice.principal;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

//사용자 회원정보를 담게될 스프링 시큐리티의 객체임
public class CustomUserDetails implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        //데이터베이스에서 불러온 이 유저의 Role, Permission을 여기서 가져와서 대량으로 저장..

        return List.of();
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return "";
    }
}