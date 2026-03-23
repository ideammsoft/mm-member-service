package co.kr.mmsoft.mmmemberservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MSSQL manymen_ppurio DataSource - SMS 전송 등록용
 * ums_data 테이블에 INSERT 하여 문자 발송 요청
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mssql-ppurio", name = "jdbc-url")
@ConditionalOnClass(name = "com.microsoft.sqlserver.jdbc.SQLServerDriver")
public class MssqlPpurioDataSourceConfig {

    @Bean(name = "mssqlPpurioDataSource")
    @ConfigurationProperties(prefix = "app.mssql-ppurio")
    public DataSource mssqlPpurioDataSource() {
        return DataSourceBuilder.create().build();
    }
}
