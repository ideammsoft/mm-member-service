package co.kr.mmsoft.mmmemberservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 정적 파일 서빙 설정
 * PDS_FILE_PATH 폴더의 이미지를 /images/pds/** 경로로 제공
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.pds-file-path:C:/mm-file/pds/}")
    private String pdsFilePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = pdsFilePath.endsWith("/") ? "file:" + pdsFilePath : "file:" + pdsFilePath + "/";
        registry.addResourceHandler("/images/pds/**")
                .addResourceLocations(location);
    }
}
