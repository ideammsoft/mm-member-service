package co.kr.mmsoft.mmmemberservice.controller;

import co.kr.mmsoft.mmmemberservice.dto.AuthTokens;
import co.kr.mmsoft.mmmemberservice.dto.LoginRequest;
import co.kr.mmsoft.mmmemberservice.dto.LoginResponse;
import co.kr.mmsoft.mmmemberservice.dto.RegistRequest;
import co.kr.mmsoft.mmmemberservice.member.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth") // 예시 경로
@RequiredArgsConstructor  // <--- 이 줄을 추가하세요!
public class AuthController {
    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    @PostMapping("/regist")
    public ResponseEntity<?> regist(@RequestBody RegistRequest registRequest){
        log.debug("전송된 openid = {}", registRequest.getOpenId());
        log.debug("전송된 password = {}", registRequest.getPassword());
        log.debug("전송된 name = {}", registRequest.getName());
        log.debug("전송된 email = {}", registRequest.getEmail());
//        //현재 컨트롤러는 홈페이지 회원가입을 전제로 한 요청을 처리하는 컨트롤러임 .. 따라서
//        //homepage라고 결정 자을수 있음.
//        Provider provider = new Provider();
//        provider.setProviderName("homepage");

        //3단계: 일시키기 ..
        authService.regist(registRequest); //현재 Provider는 누락되어 있음 ..

        return ResponseEntity.ok(Map.of(
                 "msg",  "ok"

        ));
    }
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest){
        log.debug("homepageId is {}", loginRequest.getHomepageId());
        log.debug("getPassword is {}", loginRequest.getPassword());

        //스프링 시큐리티를 타고 들어가기
        AuthTokens authTokens=authService.login((loginRequest));

        /*-----------------------------------------
        AT는 응답 Body로 전송(DTO로 처리해야 jackson의 mapper가 json으로 변환)
        --------------------------------------------*/
        LoginResponse loginResponse = new LoginResponse(authTokens.getAccessToken());

        /*-----------------------------------------
        RT는 쿠키전송
        --------------------------------------------*/
        ResponseCookie cookie = ResponseCookie.from("refreshToken", authTokens.getRefreshToken())
                .httpOnly(true)
                .secure(false) //Http, Https(true)
                .sameSite("Lax") //CSRF (사이트간 요청 위조) 공격을 막기위한 설정... Strict, Lax, None, Lax권장
                .path("/api/auth/refresh") //쿠키를 오직 지정한 url에서만 보내지도록 제한. silent refresh를 위해
                .maxAge(Duration.ofDays(14))
                .build();

        return  ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(loginResponse)
                ;
    }
}
