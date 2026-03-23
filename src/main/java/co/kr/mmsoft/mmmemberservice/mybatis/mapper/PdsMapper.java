package co.kr.mmsoft.mmmemberservice.mybatis.mapper;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Pds;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PdsMapper {
    List<Pds> findAll(@Param("category") String category);
    Pds findById(@Param("pdsId") Integer pdsId);
    int incrementDownloadCount(@Param("pdsId") Integer pdsId);
}
