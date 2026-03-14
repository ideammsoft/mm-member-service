package co.kr.mmsoft.mmmemberservice.mybatis.mapper;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.AccountRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * =====================================================================
 * [AccountRoleMapper.java] - account_role 테이블 저장 매퍼 인터페이스
 * =====================================================================
 *
 * 📌 이 파일이 뭔가요?
 *   - "어떤 회원이 어떤 역할을 가졌는지"를 저장하는 Mapper입니다.
 *   - 실제 SQL은 AccountRoleMapper.xml에 있습니다.
 *
 * 📌 사용 시점
 *   - 회원가입 완료 직후, 신규 회원에게 "customer" 역할을 부여할 때 사용합니다.
 */
@Mapper
public interface AccountRoleMapper {

    /**
     * 회원-역할 연결 데이터를 account_role 테이블에 저장합니다.
     *
     * @param accountRole 저장할 회원-역할 연결 객체 (account.accountId, role.roleId 사용됨)
     * @return 저장된 행 수 (성공하면 1)
     */
    int insert(AccountRole accountRole);
}
