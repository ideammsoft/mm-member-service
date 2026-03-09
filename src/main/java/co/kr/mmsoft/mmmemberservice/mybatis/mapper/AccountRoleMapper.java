package co.kr.mmsoft.mmmemberservice.mybatis.mapper;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.AccountRole;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountRoleMapper {
    int insert(AccountRole accountRole);
}
