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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder;
    private final ProviderMapper providerMapper;
    private final AccountMapper accountMapper;
    private final RoleMapper roleMapper;
    private final AccountRoleMapper accountRoleMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRefreshTokenStore redisRefreshTokenStore;
    /*----------------------------------------
        회원 가입
     ----------------------------------------*/
    public void regist(RegistRequest registRequest){
        //현재 이 요청은 sns 회원가입이 아니므로, Provier가 homepage이다.
        Provider provider = providerMapper.findByProviderName("homepage");

        Account account = Account.builder()
                .openId(registRequest.getOpenId())
                .password(passwordEncoder.encode(registRequest.getPassword()))
                .name(registRequest.getName())
                .email(registRequest.getEmail())
                .phone(registRequest.getPhone())
                .company(registRequest.getCompany())
                .provider(provider)
                .build();

        //Account에 들어있는 평문 비밀번호를 BCript 암호화 시키자
        int affected = accountMapper.insert(account);
        Role role = roleMapper.findByRoleName("customer");
        AccountRole accountRole = new AccountRole(account, role);
        accountRoleMapper.insert(accountRole); //이 회원이 보유한 Role 등록

        log.debug("회원가입 결과는{}", affected);
    }
    /*----------------------------------------
        로그인( 로그인을 시도하고 성공할 경우 인증결과인 AccessToken, RefreshToken 반환)
     ----------------------------------------*/
    public AuthTokens login(LoginRequest loginRequest){
        //스프링 시큐리티의 인증 절차가 시작
        //[1]인증 시도 - 예외가 발생한 경우, 로그인 실패로 간주.. 로그인 성공인 경우 Authentication Token이 반환
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(loginRequest.getHomepageId(), loginRequest.getPassword()));

        //로그인 성공 결과로 받은 Authentication Token안의 내용은
        //principal - CustomUserDetails,  credentials-null, authorities=권한들, authenticated=true 가 채워져 있음
        CustomUserDetails customUserDetails = (CustomUserDetails)authentication.getPrincipal();
        Account account = customUserDetails.getAccount();

        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a-> a!=null && a.startsWith("ROLE_")) //ROLE_ADMIN, ROLE_ADMIN 두번중복이면 하나만->distinct
                .distinct() //중복 배제
                .toList(); //최종 결과를 List 형태로 반환

        //AT(AccessToken)발급
        String accessToken = jwtTokenProvider.createAccessToken(account.getAccountId(), roles);


        //RT(RefreshToken)발급
        String jti = UUID.randomUUID().toString(); //frefresh token에 사용될 JWT ID
        String refreshToken = jwtTokenProvider.createRefreshToken(account.getAccountId(), jti);

        //RT를 Redis 저장하되(보안상 원본을 그대로 넣지말고 암호화 시켜 넣자
        redisRefreshTokenStore.save(
                jwtTokenProvider.sha256(refreshToken),
                new RefreshTokenRecord(account.getAccountId(), jti),
                jwtTokenProvider.refreshTtl()
        );
        //클라이언트에게 at, rt전송
        //AT: 응댭 Body(JSON)
        //RT: Cookie(HttpOnly)
        return new AuthTokens(accessToken, refreshToken);
    }
    /*----------------------------------------
        회원 가입 시 아이디 체크
     ----------------------------------------*/
    public int idCheck(String openId){
        int cnt = accountMapper.checkByOpenId(openId);
        return cnt;
    }
    /*----------------------------------------
        회원 아이디 찾기
     ----------------------------------------*/
    public String idFind(String email, String phone) { // 리턴 타입 String
        // 문자열 비교는 .equals() 또는 .isEmpty() 권장
        if (email != null && !email.isEmpty()) {
            log.debug("이메일로 찾기{}", email);
            String returnId = accountMapper.idFindByEmail(email);
            log.debug("이메일로 찾은 id{}", returnId);
            return returnId; // 바로 return
        }
        if (phone != null && !phone.isEmpty()) {
            return accountMapper.idFindByPhone(phone); // 바로 return
        }
        return null;
    }
    /*----------------------------------------
        회원 패스워드 찾기
     ----------------------------------------*/
    public String passwordFind(IdPassFindRequest request) { // 리턴 타입 String
        int cnt = 0;
        String email = request.getEmail();
        String openid = request.getOpenId();
        String phone = request.getPhone();

        // 1. 이메일 또는 전화번호로 사용자 존재 여부 확인
        if (email != null && !email.isEmpty()) {
            cnt = accountMapper.passwordFindByEmail(request);
        } else if (phone != null && !phone.isEmpty()) {
            cnt = accountMapper.passwordFindByPhone(request);
        }

        String shaPassword = ""; // 초기값

        // 2. 사용자가 존재할 경우(cnt > 0)에만 랜덤 비밀번호 생성
        if (cnt > 0) {
            String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
            shaPassword = new Random().ints(8, 0, chars.length())
                    .mapToObj(chars::charAt)
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                    .toString();

            // 3. TODO: 여기서 실제로 DB의 비밀번호를 업데이트하는 쿼리가 실행되어야 합니다.
            request.setIdOrPass(passwordEncoder.encode(shaPassword));
            int result = accountMapper.updatePassword(request);
            if (result == 0) shaPassword = "";
            log.debug("변경된 password : {}", shaPassword);
            // log.debug("임시 비밀번호 생성 완료: {}", shaPassword);
        }

        // 4. 생성된 비밀번호(또는 빈값)를 리턴하면 변수들의 밑줄이 사라집니다.
        return shaPassword.isEmpty() ? null : shaPassword;
    }
}
