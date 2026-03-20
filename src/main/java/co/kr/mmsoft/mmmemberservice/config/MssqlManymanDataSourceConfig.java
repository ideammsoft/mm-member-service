package co.kr.mmsoft.mmmemberservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * MSSQL manymen_bank DataSource - manyman 테이블 동기화용
 * mm-member-service에서 회원 정보 변경 시 구회원(manyman) 테이블도 업데이트
 */
@Configuration
@ConditionalOnProperty(prefix = "app.mssql-manyman", name = "jdbc-url")
@ConditionalOnClass(name = "com.microsoft.sqlserver.jdbc.SQLServerDriver")
public class MssqlManymanDataSourceConfig {

    @Bean(name = "mssqlManymanDataSource")
    @ConfigurationProperties(prefix = "app.mssql-manyman")
    public DataSource mssqlManymanDataSource() {
        return DataSourceBuilder.create().build();
    }
}
