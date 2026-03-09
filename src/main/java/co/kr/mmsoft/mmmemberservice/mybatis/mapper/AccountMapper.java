package co.kr.mmsoft.mmmemberservice.mybatis.mapper;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import org.apache.ibatis.annotations.Mapper;

//고전적으로 사용해 왔던 SqlSessionTemplate을 사용하여 CRUD를 사용하는 DAO는 사용하지 않음
//대신 mapper.xml과 여계된 인터페이스를 사용해야함
@Mapper //Mybatis의 매퍼임을 명시, 개발자가 이 인터페이스만을 제어하면 ㅌㅌㅌㅌMapper.xml이 실행되어짐
public interface AccountMapper {
    int insert(Account account);
    Account findByOpenId(String openId);
}
