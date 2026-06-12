package co.kr.mmsoft.mmmemberservice.mybatis.mapper;

import co.kr.mmsoft.mmmemberservice.dto.IdPassFindRequest;
import co.kr.mmsoft.mmmemberservice.dto.MemberProfileUpdateRequest;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * =====================================================================
 * [AccountMapper.java] - account 테이블 CRUD 매퍼 인터페이스
 * =====================================================================
 *
 * 📌 이 파일이 뭔가요?
 *   - MyBatis Mapper 인터페이스입니다.
 *   - 인터페이스란 "이런 기능이 있어야 한다"는 약속(설계도)입니다.
 *   - 실제 SQL 쿼리는 AccountMapper.xml 파일에 작성되어 있습니다.
 *   - MyBatis가 이 인터페이스와 XML을 자동으로 연결해 줍니다.
 *
 * 📌 @Mapper 어노테이션
 *   - "이 인터페이스는 MyBatis Mapper입니다"라고 스프링에게 알려주는 표시입니다.
 *   - 스프링이 자동으로 구현체(실제 동작하는 클래스)를 만들어 줍니다.
 *   - 개발자는 인터페이스만 선언하면 됩니다!
 *
 * 📌 @Param 어노테이션
 *   - 메서드 매개변수가 2개 이상일 때 XML에서 이름으로 구분하기 위해 사용합니다.
 *   - 예) @Param("providerName") → XML에서 #{providerName}으로 사용
 */
@Mapper
public interface AccountMapper {

    /**
     * 새 회원 정보를 account 테이블에 저장합니다.
     * useGeneratedKeys="true" 설정 덕분에 저장 후 자동생성된 account_id가
     * 매개변수 account 객체의 accountId 필드에 자동으로 채워집니다.
     *
     * @param account 저장할 회원 정보 (openId, password, name, email 등)
     * @return 실제로 저장된 행의 수 (성공하면 1, 실패하면 0)
     */
    int insert(Account account);

    /**
     * 로그인 아이디(openId)로 회원 정보를 조회합니다.
     * 결과에는 Account 정보 + Provider 정보 + Role/Permission 목록이 모두 포함됩니다.
     * (AccountMapper.xml의 <resultMap>과 <collection> 태그 덕분)
     *
     * @param openId 조회할 로그인 아이디
     * @return 찾은 회원 정보 (없으면 null)
     */
    Account findByOpenId(String openId);

    /**
     * 아이디 중복 확인 - 이미 가입된 아이디인지 체크합니다.
     *
     * @param openId 중복 확인할 아이디
     * @return 같은 아이디를 가진 회원 수 (0이면 사용 가능, 1이면 이미 사용 중)
     */
    int checkByOpenId(String openId);

    /** homepage_id 중복 확인 */
    int checkByHomepageId(@Param("homepageId") String homepageId);

    /**
     * 이메일로 아이디 찾기 - 이메일 주소로 로그인 아이디를 조회합니다.
     *
     * @param email 아이디를 찾을 이메일 주소
     * @return 찾은 로그인 아이디 문자열 (없으면 null)
     */
    String idFindByEmail(String email);

    /**
     * 전화번호로 아이디 찾기 - 전화번호로 로그인 아이디를 조회합니다.
     *
     * @param phone 아이디를 찾을 전화번호
     * @return 찾은 로그인 아이디 문자열 (없으면 null)
     */
    String idFindByPhone(String phone);

    /**
     * 이메일로 비밀번호 찾기 - 아이디 + 이메일이 일치하는 회원이 존재하는지 확인합니다.
     *
     * @param request 아이디(openId)와 이메일(email)이 담긴 요청 객체
     * @return 일치하는 회원 수 (0이면 없음, 1이면 존재)
     */
    int passwordFindByEmail(IdPassFindRequest request);

    /**
     * 전화번호로 비밀번호 찾기 - 아이디 + 전화번호가 일치하는 회원이 존재하는지 확인합니다.
     *
     * @param request 아이디(openId)와 전화번호(phone)이 담긴 요청 객체
     * @return 일치하는 회원 수 (0이면 없음, 1이면 존재)
     */
    int passwordFindByPhone(IdPassFindRequest request);

    /**
     * 비밀번호를 새로운 값으로 변경합니다.
     * (임시 비밀번호 발급 시 사용)
     *
     * @param request openId(아이디)와 idOrPass(새 암호화 비밀번호)가 담긴 요청 객체
     * @return 실제로 변경된 행의 수 (성공하면 1)
     */
    int updatePassword(IdPassFindRequest request);

    /**
     * OAuth2(소셜 로그인) 전용 - 공급자 이름 + openId로 회원을 조회합니다.
     * 소셜 로그인 시 이미 가입된 회원인지 확인하거나, 회원 정보를 가져올 때 사용합니다.
     *
     * 📌 @Param을 사용하는 이유
     *   매개변수가 2개이므로 XML에서 #{providerName}, #{openId}로 구분하려면 필요합니다.
     *   만약 @Param이 없으면 XML에서 어떤 값인지 구분할 수 없어 에러가 발생합니다.
     *
     * @param providerName 공급자 이름 (예: "google", "naver", "kakao")
     * @param openId       공급자가 제공한 사용자 고유 식별값
     * @return 찾은 회원 정보 (없으면 null → 신규 회원으로 자동 가입 처리)
     */
    Account findByProviderAndOpenId(@Param("providerName") String providerName,
                                    @Param("openId") String openId);

    /** accountId(PK)로 회원 조회 - JWT 필터에서 사용 */
    Account findByAccountId(Long accountId);

    /** 회원 정보 수정 (phone, email, name, company) */
    int updateProfile(MemberProfileUpdateRequest request);

    /** 이메일 미설정 회원에게만 이메일 저장 */
    int updateEmailIfEmpty(@Param("accountId") Long accountId, @Param("email") String email);

    /** 회원 탈퇴 */
    int deleteByAccountId(Long accountId);

    /** 비밀번호 변경 (로그인한 회원 본인) */
    int updatePasswordByAccountId(@Param("accountId") Long accountId, @Param("password") String password);
}
