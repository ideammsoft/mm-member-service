package co.kr.mmsoft.mmmemberservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
    "spring.config.import=optional:configserver:http://localhost:1881",
    "spring.security.oauth2.client.registration.google.client-id=test",
    "spring.security.oauth2.client.registration.google.client-secret=test",
    "spring.security.oauth2.client.registration.google.redirect-uri=http://localhost/login/oauth2/code/google",
    "spring.security.oauth2.client.registration.google.scope=openid,profile,email",
    "spring.security.oauth2.client.registration.naver.client-id=test",
    "spring.security.oauth2.client.registration.naver.client-secret=test",
    "spring.security.oauth2.client.registration.naver.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.naver.redirect-uri=http://localhost/login/oauth2/code/naver",
    "spring.security.oauth2.client.registration.naver.scope=name,email",
    "spring.security.oauth2.client.provider.naver.authorization-uri=https://nid.naver.com/oauth2.0/authorize",
    "spring.security.oauth2.client.provider.naver.token-uri=https://nid.naver.com/oauth2.0/token",
    "spring.security.oauth2.client.provider.naver.user-info-uri=https://openapi.naver.com/v1/nid/me",
    "spring.security.oauth2.client.provider.naver.user-name-attribute=response",
    "spring.security.oauth2.client.registration.kakao.client-id=test",
    "spring.security.oauth2.client.registration.kakao.client-secret=test",
    "spring.security.oauth2.client.registration.kakao.client-authentication-method=client_secret_post",
    "spring.security.oauth2.client.registration.kakao.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.kakao.redirect-uri=http://localhost/login/oauth2/code/kakao",
    "spring.security.oauth2.client.registration.kakao.scope=profile_nickname",
    "spring.security.oauth2.client.provider.kakao.authorization-uri=https://kauth.kakao.com/oauth/authorize",
    "spring.security.oauth2.client.provider.kakao.token-uri=https://kauth.kakao.com/oauth/token",
    "spring.security.oauth2.client.provider.kakao.user-info-uri=https://kapi.kakao.com/v2/user/me",
    "spring.security.oauth2.client.provider.kakao.user-name-attribute=id",
    "spring.mail.host=smtp.gmail.com",
    "spring.mail.port=587",
    "spring.mail.username=test@gmail.com",
    "spring.mail.password=test-password",
    "spring.mail.properties.mail.smtp.auth=true",
    "spring.mail.properties.mail.smtp.starttls.enable=true"
})
class MmMemberServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
