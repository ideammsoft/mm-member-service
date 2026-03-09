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
}
