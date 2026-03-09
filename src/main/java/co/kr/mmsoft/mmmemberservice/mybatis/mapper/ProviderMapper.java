package co.kr.mmsoft.mmmemberservice.mybatis.mapper;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Provider;
import org.apache.ibatis.annotations.Mapper;

@Mapper //Mybatis Spring의 매퍼임을 선언
public interface ProviderMapper {
    Provider findByProviderName(String providerName);
}
