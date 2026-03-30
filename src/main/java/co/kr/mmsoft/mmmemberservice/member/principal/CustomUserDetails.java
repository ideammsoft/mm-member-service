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

/**
 * =====================================================================
 * [CustomUserDetails.java] - Spring Security에 회원 정보를 전달하는 클래스
 * =====================================================================
 *
 * 📌 이 클래스가 왜 필요한가요?
 *   - Spring Security는 로그인한 사용자 정보를 "UserDetails" 형태로 관리합니다.
 *   - UserDetails는 Spring Security가 정한 인터페이스(약속)입니다.
 *   - 우리 시스템의 Account 클래스를 UserDetails 형태로 "포장"해주는 역할입니다.
 *
 * 📌 작동 흐름
 *   1. 사용자가 로그인 시도 (아이디 + 비밀번호 전송)
 *   2. Spring Security가 CustomUserDetailsService.loadUserByUsername() 호출
 *   3. DB에서 Account 객체를 가져옴
 *   4. Account를 CustomUserDetails로 감싸서 반환
 *   5. Spring Security가 비밀번호 검증 등을 자동으로 처리
 *
 * 📌 UserDetails 인터페이스의 주요 메서드
 *   - getAuthorities() : 이 사용자의 권한 목록 반환 (Role + Permission)
 *   - getPassword()    : 암호화된 비밀번호 반환 (Spring Security가 검증에 사용)
 *   - getUsername()    : 로그인 아이디 반환 (Spring Security에서 "username"이라 부름)
 *
 * 📌 @Slf4j 어노테이션
 *   - log.debug(), log.info() 등 로그 출력 기능을 자동으로 제공합니다.
 *   - System.out.println() 대신 사용합니다 (운영 환경에서 끌 수 있어 효율적).
 */
@Slf4j
@Getter
public class CustomUserDetails implements UserDetails {

    // 이 클래스가 포장하고 있는 실제 회원 정보
    // @Getter 덕분에 getAccount() 메서드가 자동 생성됩니다
    private Account account;

    /**
     * 생성자 - Account 객체를 받아서 CustomUserDetails로 감쌉니다.
     *
     * @param account DB에서 조회한 회원 정보 (Role, Permission 목록 포함)
     */
    public CustomUserDetails(Account account) {
        this.account = account;
    }

    /**
     * 이 회원의 역할(Role)과 권한(Permission) 목록을 반환합니다.
     * Spring Security가 인가(Authorization) 처리에 사용합니다.
     *
     * 📌 반환 형식
     *   - 역할(Role): "ROLE_" 접두사를 붙여서 추가 → 예) "ROLE_customer", "ROLE_admin"
     *     → Spring Security는 역할을 "ROLE_"로 시작하는 문자열로 인식합니다.
     *   - 권한(Permission): 그대로 추가 → 예) "NOTICE_CREATE", "PRODUCT_DELETE"
     *
     * @return 역할 + 권한이 모두 담긴 목록
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 역할과 권한을 담을 빈 목록을 준비
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // 역할이 없으면 일반 사용자(customer)로 기본 적용
        if (account.getRoleList() == null || account.getRoleList().isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_customer"));
            return authorities;
        }

        // 이 회원이 가진 모든 역할을 순서대로 처리
        for (Role role : account.getRoleList()) {
            log.debug("이 계정이 보유한 역할의 이름: {}", role.getRoleName());

            // 역할 추가: "ROLE_" + 역할이름 형식으로 추가
            // 예) roleName이 "customer"이면 → "ROLE_customer"
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRoleName()));

            // 이 역할이 가진 모든 권한도 추가
            for (Permission permission : role.getPermissionList()) {
                log.debug("이 역할이 가진 권한: {}", permission.getPermissionName());
                // 권한은 "ROLE_" 없이 그대로 추가 (예: "NOTICE_CREATE")
                authorities.add(new SimpleGrantedAuthority(permission.getPermissionName()));
            }
        }
        return authorities;
    }

    /**
     * 암호화된 비밀번호를 반환합니다.
     * Spring Security가 로그인 시 입력된 비밀번호와 비교할 때 사용합니다.
     */
    @Override
    public String getPassword() {
        return account.getPassword();
    }

    /**
     * 로그인 아이디를 반환합니다.
     * Spring Security에서는 로그인 아이디를 "username"이라고 부릅니다.
     * 우리 시스템에서는 openId가 로그인 아이디 역할을 합니다.
     */
    @Override
    public String getUsername() {
        return account.getOpenId();
    }
}
