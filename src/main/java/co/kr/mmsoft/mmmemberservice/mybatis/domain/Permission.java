package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * =====================================================================
 * [Permission.java] - 세부 권한 정보를 담는 클래스 (도메인 객체)
 * =====================================================================
 *
 * 📌 이 클래스가 뭔가요?
 *   - 데이터베이스의 "permission" 테이블 1개 행(row)에 해당하는 Java 클래스입니다.
 *   - Permission(권한)은 Role(역할)보다 더 세밀한 기능 단위입니다.
 *
 * 📌 역할(Role) vs 권한(Permission) 차이
 *   - Role    : 사용자 유형 → 예) super_admin, admin, customer
 *   - Permission : 구체적인 기능 허용 → 예) PRODUCT_CREATE, NOTICE_DELETE
 *
 * 📌 DB 테이블 구조 (V1__init.sql 참고)
 *   CREATE TABLE permission (
 *       permission_id   INT PRIMARY KEY AUTO_INCREMENT,
 *       permission_name VARCHAR(25) NOT NULL
 *   );
 *
 * 📌 V2__seed_data.sql에 등록된 권한 예시
 *   PRODUCT_CREATE, PRODUCT_UPDATE, PRODUCT_DELETE,
 *   NOTICE_CREATE, NOTICE_UPDATE, NOTICE_DELETE, NOTICE_VIEW
 */
@Getter
@Setter
public class Permission {

    // permission 테이블의 permission_id 컬럼
    private int permissionId;

    // permission 테이블의 permission_name 컬럼
    // 값 예시: "PRODUCT_CREATE", "NOTICE_VIEW"
    private String permissionName;
}
