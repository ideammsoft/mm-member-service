package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * =====================================================================
 * [Role.java] - 사용자 역할(등급) 정보를 담는 클래스 (도메인 객체)
 * =====================================================================
 *
 * 📌 이 클래스가 뭔가요?
 *   - 데이터베이스의 "role" 테이블 1개 행(row)에 해당하는 Java 클래스입니다.
 *   - 사용자가 어떤 역할인지 나타냅니다. (관리자인지, 일반 고객인지 등)
 *
 * 📌 DB 테이블 구조 (V1__init.sql 참고)
 *   CREATE TABLE role (
 *       role_id   INT PRIMARY KEY AUTO_INCREMENT,
 *       role_name VARCHAR(15) NOT NULL
 *   );
 *
 * 📌 V2__seed_data.sql에 등록된 역할 예시
 *   super_admin (최고관리자), admin (관리자/직원), customer (일반 고객)
 *
 * 📌 permissionList 필드 설명
 *   - 하나의 역할은 여러 개의 권한(Permission)을 가질 수 있습니다.
 *   - 예) admin 역할은 NOTICE_CREATE, NOTICE_UPDATE 등 여러 권한 보유
 *   - Java의 List<Permission>은 여러 개를 담는 "목록(배열과 비슷)"입니다.
 *   - MyBatis의 <collection> 태그로 자동으로 채워집니다. (RoleMapper.xml 참고)
 */
@Getter
@Setter
public class Role {

    // role 테이블의 role_id 컬럼
    private int roleId;

    // role 테이블의 role_name 컬럼
    // 값 예시: "super_admin", "admin", "customer"
    private String roleName;

    // 이 역할이 보유한 Permission(권한) 목록
    // DB에서는 role_permission 테이블을 통해 연결됩니다 (다대다 관계)
    // RoleMapper.xml의 <collection> 태그가 자동으로 이 목록을 채워줍니다
    private List<Permission> permissionList;
}
