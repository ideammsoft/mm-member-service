package co.kr.mmsoft.mmmemberservice.mybatis.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * =====================================================================
 * [Account.java] - 회원(사용자) 정보를 담는 클래스 (도메인 객체)
 * =====================================================================
 *
 * 📌 이 클래스가 뭔가요?
 *   - 데이터베이스의 "account" 테이블 1개 행(row)에 해당하는 Java 클래스입니다.
 *   - 시스템에 가입한 사용자 1명의 정보를 Java 코드로 표현합니다.
 *
 * 📌 DB 테이블 구조 (V1__init.sql 참고)
 *   CREATE TABLE account (
 *       account_id  INT PRIMARY KEY AUTO_INCREMENT,
 *       open_id     VARCHAR(255),   -- 로그인 아이디 (홈페이지) 또는 SNS 고유키
 *       password    VARCHAR(64),    -- 암호화된 비밀번호
 *       name        VARCHAR(25),    -- 이름
 *       email       VARCHAR(25),    -- 이메일
 *       phone       VARCHAR(25),    -- 전화번호
 *       company     VARCHAR(50),    -- 회사명
 *       provider_id INT             -- 로그인 공급자 (FK → provider 테이블)
 *   );
 *
 * 📌 Lombok 어노테이션 설명
 *   @Getter       → get 메서드 자동 생성
 *   @Setter       → set 메서드 자동 생성
 *   @NoArgsConstructor → 매개변수 없는 기본 생성자 자동 생성
 *                        (MyBatis가 DB 결과를 자동으로 이 클래스에 담으려면 필수!)
 *
 * 📌 객체 간 관계 설명 (테이블 연결)
 *   - account ↔ provider  : 회원 1명은 공급자 1개 (일대일 관계)
 *                            → Java에서는 Provider 객체 1개로 표현
 *   - account ↔ role      : 회원 1명은 여러 역할 가능 (일대다 관계)
 *                            → Java에서는 List<Role>로 표현
 *
 * 📌 Builder 패턴이란?
 *   - 객체를 만들 때 어떤 필드에 어떤 값을 넣는지 명확하게 표현하는 방법입니다.
 *   - 일반 생성자: new Account(1L, "홍길동", "1234", ...) → 순서 헷갈림 위험
 *   - Builder:    Account.builder().name("홍길동").openId("hong").build() → 명확!
 */
@Getter
@Setter
@NoArgsConstructor  // MyBatis가 내부적으로 빈 객체를 만들 때 반드시 필요한 기본 생성자
public class Account {

    // account 테이블의 account_id 컬럼 (Long = 큰 정수)
    private Long accountId;

    // account 테이블의 open_id 컬럼
    // 홈페이지 직접 가입: 사용자가 입력한 아이디 (예: "hong123")
    // SNS 로그인: 구글/네이버/카카오가 제공하는 고유 식별값
    private String openId;

    // account 테이블의 homepage_id 컬럼
    // 결제/문자 충전에 사용하는 서비스 고유 ID
    // 일반 가입: openId와 동일, OAuth 가입: 이메일 앞부분 자동 생성
    private String homepageId;

    // account 테이블의 password 컬럼
    // BCrypt로 암호화된 비밀번호가 저장됩니다 (평문 저장 절대 금지!)
    // SNS 로그인 회원은 null입니다
    private String password;

    // 사용자 실명
    private String name;

    // 이메일 주소
    private String email;

    // 휴대폰 번호
    private String mphone;

    // 회사 전화
    private String phone;

    // 회사명 (선택 입력)
    private String company;

    // 이 회원의 로그인 공급자 정보 (Provider 객체 전체가 들어옵니다)
    // MyBatis의 <association> 태그로 자동으로 채워집니다 (AccountMapper.xml 참고)
    // 예) provider.providerName = "google"
    private Provider provider;

    // 이 회원이 가진 역할(Role) 목록
    // 한 명의 회원은 여러 역할을 가질 수 있습니다
    // MyBatis의 <collection> 태그로 자동으로 채워집니다 (AccountMapper.xml 참고)
    private List<Role> roleList;

    /**
     * Builder 패턴용 생성자
     * @Builder 어노테이션이 이 생성자를 기반으로 빌더를 만들어 줍니다.
     *
     * 사용 예시:
     *   Account account = Account.builder()
     *       .openId("hong123")
     *       .name("홍길동")
     *       .email("hong@example.com")
     *       .provider(provider)
     *       .build();
     */
    @Builder
    public Account(Long accountId, String openId, String homepageId, String password, String name,
                   String email, String mphone, String phone, String company, Provider provider) {
        this.accountId  = accountId;
        this.openId     = openId;
        this.homepageId = homepageId;
        this.password   = password;
        this.name       = name;
        this.email      = email;
        this.mphone     = mphone;
        this.phone      = phone;
        this.company    = company;
        this.provider   = provider;
    }
}
