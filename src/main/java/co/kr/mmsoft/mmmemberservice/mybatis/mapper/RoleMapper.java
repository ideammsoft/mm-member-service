package co.kr.mmsoft.mmmemberservice.mybatis.mapper;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Role;
import org.apache.ibatis.annotations.Mapper;

/**
 * =====================================================================
 * [RoleMapper.java] - role 테이블 조회 매퍼 인터페이스
 * =====================================================================
 *
 * 📌 실제 SQL은 RoleMapper.xml에 작성되어 있습니다.
 *
 * 📌 사용 위치
 *   - AuthService: 회원가입 완료 후 "customer" 역할을 부여하기 위해 사용
 *   - CustomOAuth2UserService: 소셜 로그인 신규 회원에게 "customer" 역할 부여
 */
@Mapper
public interface RoleMapper {

    /**
     * 역할 이름으로 Role 정보를 조회합니다.
     *
     * @param roleName 조회할 역할 이름 (예: "customer", "admin", "super_admin")
     * @return 찾은 Role 객체 (roleId, roleName 포함)
     */
    Role findByRoleName(String roleName);
}
