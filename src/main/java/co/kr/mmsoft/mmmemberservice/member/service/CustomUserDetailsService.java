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

/**
 * =====================================================================
 * [CustomUserDetailsService.java] - Spring Security 로그인 처리 서비스
 * =====================================================================
 *
 * 📌 이 클래스가 왜 필요한가요?
 *   - Spring Security는 로그인 처리를 위해 UserDetailsService 인터페이스를 사용합니다.
 *   - UserDetailsService는 "아이디로 회원 정보를 어떻게 조회할까?"를 정의하는 약속입니다.
 *   - 우리가 직접 구현하면 스프링이 로그인할 때 자동으로 이 클래스를 호출합니다.
 *
 * 📌 작동 흐름 (로그인 시)
 *   1. 사용자: 아이디/비밀번호 입력 후 로그인 요청
 *   2. Spring Security: loadUserByUsername(아이디) 자동 호출
 *   3. 이 메서드: DB에서 해당 아이디의 회원 정보 조회
 *   4. 회원 정보를 CustomUserDetails로 감싸서 반환
 *   5. Spring Security: 비밀번호 자동 검증 (맞으면 로그인 성공, 틀리면 실패)
 *
 * 📌 어노테이션 설명
 *   @Service          → 이 클래스가 "비즈니스 로직을 처리하는 서비스"임을 스프링에게 알림
 *   @RequiredArgsConstructor → final로 선언된 필드(accountMapper)를 자동으로 주입받는 생성자 생성
 *                              (Spring의 의존성 주입 - Dependency Injection)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    // AccountMapper: DB의 account 테이블에서 회원 정보를 조회하는 객체
    // final 키워드: 이 필드는 한번 설정되면 변경 불가 (불변성 보장)
    // @RequiredArgsConstructor가 생성자를 통해 자동으로 스프링 빈을 주입합니다
    private final AccountMapper accountMapper;

    /**
     * 로그인 아이디(openId)로 회원 정보를 조회하여 Spring Security에게 전달합니다.
     * Spring Security가 로그인 처리 시 자동으로 이 메서드를 호출합니다.
     *
     * @param username Spring Security에서 로그인 아이디를 "username"이라고 부릅니다.
     *                 우리 시스템에서는 openId가 이 역할을 합니다.
     * @return CustomUserDetails 객체 (Spring Security가 비밀번호 검증에 사용)
     * @throws UsernameNotFoundException 해당 아이디의 회원이 DB에 없을 때 발생
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // DB에서 아이디로 회원 정보 조회
        // Account 안에는 회원 기본정보 + Provider + Role + Permission이 모두 들어있습니다
        Account account = accountMapper.findByOpenId(username);

        // 회원이 없으면 예외 발생 → Spring Security가 로그인 실패로 처리
        if (account == null) {
            throw new UsernameNotFoundException("해당 아이디의 회원을 찾을 수 없습니다: " + username);
        }

        log.debug("로그인한 유저의 이메일: {}", account.getEmail());
        if (account.getRoleList() != null && !account.getRoleList().isEmpty()) {
            log.debug("로그인한 유저의 역할: {}", account.getRoleList().get(0).getRoleName());
        } else {
            log.debug("로그인한 유저의 역할 없음 → customer 기본 적용");
        }

        // Account를 CustomUserDetails로 감싸서 반환
        // Spring Security가 이 객체로 비밀번호를 검증합니다
        return new CustomUserDetails(account);
    }
}
