package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * =====================================================================
 * [AccountRole.java] - 회원-역할 연결 정보를 담는 클래스 (도메인 객체)
 * =====================================================================
 *
 * 📌 이 클래스가 뭔가요?
 *   - 데이터베이스의 "account_role" 테이블에 해당하는 Java 클래스입니다.
 *   - "어떤 회원이 어떤 역할을 가지는가"를 연결하는 중간 테이블입니다.
 *
 * 📌 DB 테이블 구조 (V1__init.sql 참고)
 *   CREATE TABLE account_role (
 *       account_role_id INT PRIMARY KEY AUTO_INCREMENT,
 *       account_id      INT,  -- 어떤 회원인지 (FK → account 테이블)
 *       role_id         INT   -- 어떤 역할인지 (FK → role 테이블)
 *   );
 *
 * 📌 사용 예시 (AuthService.java의 회원가입 코드 참고)
 *   // 회원가입 완료 후 "customer" 역할을 이 회원에게 부여할 때 사용
 *   Role role = roleMapper.findByRoleName("customer");
 *   AccountRole accountRole = new AccountRole(account, role);
 *   accountRoleMapper.insert(accountRole);  // DB에 저장
 */
@Getter
@Setter
public class AccountRole {

    // account_role 테이블의 account_role_id 컬럼 (자동 증가 번호)
    private int accountRoleId;

    // 어떤 회원에게 역할을 부여하는가 (Account 객체 전체가 들어옵니다)
    // DB에 저장할 때는 account.accountId 값만 사용합니다 (AccountRoleMapper.xml 참고)
    private Account account;

    // 어떤 역할을 부여하는가 (Role 객체 전체가 들어옵니다)
    // DB에 저장할 때는 role.roleId 값만 사용합니다
    private Role role;

    /**
     * 생성자 - 회원 + 역할 정보를 받아서 AccountRole 객체 생성
     *
     * @param account 역할을 부여받을 회원 객체
     * @param role    부여할 역할 객체
     */
    public AccountRole(Account account, Role role) {
        this.account = account;
        this.role    = role;
    }
}
