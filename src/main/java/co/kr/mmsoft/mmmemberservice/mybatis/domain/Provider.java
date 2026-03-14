package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * =====================================================================
 * [Provider.java] - 로그인 공급자(제공자) 정보를 담는 클래스 (도메인 객체)
 * =====================================================================
 *
 * 📌 이 클래스가 뭔가요?
 *   - 데이터베이스의 "provider" 테이블 1개 행(row)에 해당하는 Java 클래스입니다.
 *   - 로그인 방법이 어디인지 구분합니다.
 *     예) google, naver, kakao, homepage(직접 회원가입)
 *
 * 📌 DB 테이블 구조 (V1__init.sql 참고)
 *   CREATE TABLE provider (
 *       provider_id   INT PRIMARY KEY AUTO_INCREMENT,  -- 자동 증가 번호
 *       provider_name VARCHAR(50) NOT NULL              -- 공급자 이름
 *   );
 *
 * 📌 Lombok 어노테이션 설명
 *   @Getter  → 각 필드의 get메서드를 자동으로 만들어 줍니다 (getProviderId, getProviderName)
 *   @Setter  → 각 필드의 set메서드를 자동으로 만들어 줍니다 (setProviderId, setProviderName)
 *   Lombok이 없으면 직접 아래처럼 타이핑해야 합니다:
 *   public int getProviderId() { return this.providerId; }
 *   public void setProviderId(int providerId) { this.providerId = providerId; }
 */
@Getter
@Setter
public class Provider {

    // provider 테이블의 provider_id 컬럼에 해당
    // MyBatis 설정에서 map-underscore-to-camel-case: true 이므로
    // DB컬럼 provider_id → Java필드 providerId 로 자동 변환됩니다
    private int providerId;

    // provider 테이블의 provider_name 컬럼에 해당
    // 값 예시: "google", "naver", "kakao", "homepage"
    private String providerName;
}
