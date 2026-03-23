package co.kr.mmsoft.mmmemberservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정적 파일 서빙 설정
 * C:/mm-file/pds/ 폴더의 이미지를 /images/pds/** 경로로 제공
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/images/pds/**")
                .addResourceLocations("file:C:/mm-file/pds/");
    }
}
