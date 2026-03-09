package co.kr.mmsoft.mmmemberservice.member.service;

import co.kr.mmsoft.mmmemberservice.member.principal.CustomUserDetails;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService  implements UserDetailsService {

    /* 비밀번호 없이 회원의 아이디만으로 회원정보를 가져오는 기능 */
    private final AccountMapper accountMapper;


    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 사용자 명으로 회원정보를 가져오기
        //Account 안에는 이미, Role뿐만 아니라 해당 Role이 보유한 Permission 모두 들어있다 ..
        //Mybatis collection으로 자식 목록을 수집했기 때문에 ...
        Account account = accountMapper.findByOpenId(username);

        log.debug("로그인한 유저의 이메일은 {}", account.getEmail());
        log.debug("로그인한 유저의 보유 Role {}", account.getRoleList().get(0));

        //Account 안에는 비밀번호 뿐만아니라 Role, Permission 등 모든 회원정보가 들어있으므로, UserDetails로 전달하자
        return new CustomUserDetails(account);
    }
}