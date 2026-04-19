package co.kr.mmsoft.mmmemberservice;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import java.security.Security;

@EnableDiscoveryClient
@SpringBootApplication
public class MmMemberServiceApplication {

    static {
        // NICE SEED 암호화: Java 21 Linux 환경에서 JVM 기본 SEED provider 미포함
        // BouncyCastle을 최우선 provider로 등록하여 SEED 지원
        if (Security.getProvider("BC") == null) {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(MmMemberServiceApplication.class, args);
    }

}
