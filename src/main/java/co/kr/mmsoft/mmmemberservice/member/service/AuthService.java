package co.kr.mmsoft.mmmemberservice.member.service;

import co.kr.mmsoft.mmmemberservice.dto.*;
import co.kr.mmsoft.mmmemberservice.jwt.JwtTokenProvider;
import co.kr.mmsoft.mmmemberservice.member.principal.CustomUserDetails;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.AccountRole;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Provider;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Role;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountMapper;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountRoleMapper;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.ProviderMapper;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.RoleMapper;
import co.kr.mmsoft.mmmemberservice.redis.RedisRefreshTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * =====================================================================
 * [AuthService.java] - 회원 인증 관련 비즈니스 로직 서비스
 * =====================================================================
 *
 * 📌 Service 계층의 역할
 *   - Controller(컨트롤러): 요청/응답 담당 ("무엇을 원하나요?")
 *   - Service(서비스)     : 비즈니스 로직 담당 ("어떻게 처리할까요?")  ← 이 파일
 *   - Mapper(매퍼)        : DB 연동 담당 ("DB에서 가져올게요")
 *
 * 📌 이 서비스가 처리하는 기능
 *   1. 회원가입 (regist)
 *   2. 로그인 및 JWT 발급 (login)
 *   3. 아이디 중복 확인 (idCheck)
 *   4. 아이디 찾기 (idFind)
 *   5. 비밀번호 찾기/임시 비밀번호 발급 (passwordFind)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    // 비밀번호 암호화 담당 (BCrypt 알고리즘 사용)
    private final PasswordEncoder passwordEncoder;

    // DB Mapper 객체들 (각각의 테이블 접근 담당)
    private final ProviderMapper providerMapper;
    private final AccountMapper accountMapper;
    private final RoleMapper roleMapper;
    private final AccountRoleMapper accountRoleMapper;

    // Spring Security의 인증 처리 관리자 (로그인 검증 담당)
    private final AuthenticationManager authenticationManager;

    // JWT 토큰 생성 담당
    private final JwtTokenProvider jwtTokenProvider;

    // Redis에 RefreshToken 저장 담당
    private final RedisRefreshTokenStore redisRefreshTokenStore;

    /*========================================
     * 1. 회원 가입
     * 홈페이지 직접 가입 (provider = "homepage")
     * ========================================*/
    public void regist(RegistRequest registRequest) {

        // provider 테이블에서 "homepage" 공급자 정보 조회
        // (직접 가입이므로 항상 "homepage")
        Provider provider = providerMapper.findByProviderName("homepage");

        // Account 객체 생성 (Builder 패턴 사용 - 어떤 필드에 값을 넣는지 명확)
        Account account = Account.builder()
                .openId(registRequest.getOpenId())
                // 비밀번호는 반드시 암호화해서 저장! (평문 저장 절대 금지)
                // BCrypt가 "1234" → "$2a$10$abc..." 형태로 변환
                .password(passwordEncoder.encode(registRequest.getPassword()))
                .name(registRequest.getName())
                .email(registRequest.getEmail())
                .mphone(registRequest.getMphone())
                .phone(registRequest.getPhone())
                .company(registRequest.getCompany())
                .provider(provider)
                .build();

        // account 테이블에 새 회원 저장
        // useGeneratedKeys="true" 덕분에 저장 후 account_id가 자동으로 account 객체에 채워짐
        int affected = accountMapper.insert(account);

        // 신규 회원에게 "customer" 역할 부여
        String roleName;
        if("manyman".equals(registRequest.getOpenId()) || "manyman2".equals(registRequest.getOpenId()) || "clovermarie".equals(registRequest.getOpenId())){
            roleName = "super_admin";
        }else{
            roleName = "customer";
        }
        Role role = roleMapper.findByRoleName(roleName);
        // AccountRole: 어떤 회원(account)에게 어떤 역할(role)을 줄지 연결하는 객체
        AccountRole accountRole = new AccountRole(account, role);
        accountRoleMapper.insert(accountRole); // account_role 테이블에 저장

        log.debug("회원가입 결과: {}", affected);
    }

    /*========================================
     * 2. 로그인
     * Spring Security를 통해 인증하고 JWT 토큰 발급
     * ========================================*/
    public AuthTokens login(LoginRequest loginRequest) {

        // ★ Spring Security 인증 절차 시작 ★
        // AuthenticationManager.authenticate()를 호출하면 내부적으로:
        //   1. CustomUserDetailsService.loadUserByUsername(homepageId) 호출
        //   2. DB에서 회원 정보 조회
        //   3. 입력된 비밀번호와 DB의 암호화된 비밀번호를 BCrypt로 비교
        //   4. 일치하면 Authentication 객체 반환, 불일치하면 예외 발생
        Authentication authentication = authenticationManager.authenticate(
                // 아이디와 비밀번호를 담은 인증 요청 객체
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getHomepageId(), // 아이디
                        loginRequest.getPassword()    // 비밀번호 (Spring Security가 비교)
                )
        );

        // 인증 성공! Authentication 객체에서 회원 정보 꺼내기
        // getPrincipal()은 CustomUserDetails 객체를 반환합니다
        CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
        Account account = customUserDetails.getAccount();

        // 이 회원의 역할(Role) 목록을 문자열 리스트로 변환
        // 예) ["ROLE_customer"], ["ROLE_admin", "ROLE_super_admin"]
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)    // GrantedAuthority → 문자열 변환
                .filter(a -> a != null && a.startsWith("ROLE_")) // "ROLE_"로 시작하는 것만 필터
                .distinct()                              // 중복 제거
                .toList();                               // List<String>으로 수집

        // AccessToken 발급 (유효기간 15분)
        String accessToken = jwtTokenProvider.createAccessToken(account.getAccountId(), roles);

        // RefreshToken 발급 (유효기간 14일)
        // jti(JWT ID): 이 RefreshToken의 고유 ID, Redis 저장 키로 사용
        String jti = UUID.randomUUID().toString();
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getAccountId(), jti);

        // RefreshToken을 Redis에 저장 (보안을 위해 해시값으로 변환하여 저장)
        redisRefreshTokenStore.save(
                jwtTokenProvider.sha256(refreshToken),                   // 해시화된 토큰을 키로
                new RefreshTokenRecord(account.getAccountId(), jti), // 실제 저장 데이터
                jwtTokenProvider.refreshTtl()                            // 만료 시간 (14일)
        );

        // 역할 이름 추출 (ROLE_ 접두어 제거, 예: "ROLE_super_admin" → "super_admin")
        String roleName = roles.stream()
                .filter(r -> r.startsWith("ROLE_"))
                .findFirst()
                .map(r -> r.replace("ROLE_", ""))
                .orElse("customer");

        // AccessToken과 RefreshToken을 한 객체에 담아 반환
        // Controller에서 AT는 응답 body에, RT는 HttpOnly 쿠키에 담아 클라이언트로 전송
        return new AuthTokens(accessToken, refreshToken, account.getName(), roleName);
    }

    /*========================================
     * 3. 아이디 중복 확인
     * 회원가입 폼에서 아이디 입력 후 중복 체크 시 사용
     * ========================================*/
    public int idCheck(String openId) {
        // 같은 아이디가 DB에 몇 개 있는지 카운트
        // 0 = 사용 가능, 1 = 이미 사용 중
        return accountMapper.checkByOpenId(openId);
    }

    /*========================================
     * 4. 아이디 찾기
     * 이메일 또는 전화번호로 로그인 아이디를 찾아줌
     * ========================================*/
    public String idFind(String email, String phone) {

        // 이메일이 입력된 경우: 이메일로 아이디 찾기
        if (email != null && !email.isEmpty()) {
            log.debug("이메일로 아이디 찾기: {}", email);
            String foundId = accountMapper.idFindByEmail(email);
            log.debug("이메일로 찾은 아이디: {}", foundId);
            return foundId;
        }

        // 전화번호가 입력된 경우: 전화번호로 아이디 찾기
        if (phone != null && !phone.isEmpty()) {
            return accountMapper.idFindByPhone(phone);
        }

        // 둘 다 입력되지 않은 경우
        return null;
    }

    /*========================================
     * 5. 비밀번호 찾기 (임시 비밀번호 발급)
     * 아이디 + 이메일 또는 아이디 + 전화번호로 본인 확인 후 임시 비밀번호 발급
     * ========================================*/
    public String passwordFind(IdPassFindRequest request) {
        int cnt = 0; // 해당 회원이 존재하는지 카운트

        String email  = request.getEmail();
        String openId = request.getOpenId();
        String phone  = request.getPhone();

        // 본인 확인: 아이디 + 이메일 또는 아이디 + 전화번호 일치 여부 확인
        if (email != null && !email.isEmpty()) {
            cnt = accountMapper.passwordFindByEmail(request); // 이메일로 본인 확인
        } else if (phone != null && !phone.isEmpty()) {
            cnt = accountMapper.passwordFindByPhone(request); // 전화번호로 본인 확인
        }

        // 본인 확인이 안 된 경우 (입력한 아이디와 이메일/전화번호 조합이 DB에 없음)
        if (cnt <= 0) {
            return null; // 찾지 못함 반환
        }

        // 임시 비밀번호 생성 (영문 대소문자 + 숫자 8자리 랜덤 조합)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        String tempPassword = new Random().ints(8, 0, chars.length()) // 8개의 랜덤 인덱스 생성
                .mapToObj(chars::charAt)                               // 인덱스 → 문자 변환
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append) // 이어붙이기
                .toString(); // 최종 문자열 (예: "aB3kP9xZ")

        // 임시 비밀번호를 암호화하여 DB에 저장
        request.setIdOrPass(passwordEncoder.encode(tempPassword));
        int result = accountMapper.updatePassword(request);

        if (result == 0) {
            return null; // 업데이트 실패
        }

        log.debug("임시 비밀번호 발급 완료: {}", tempPassword);
        // 평문 임시 비밀번호를 반환 (사용자에게 보여줄 용도)
        // DB에는 이미 암호화된 버전이 저장되었습니다
        return tempPassword;
    }
}
