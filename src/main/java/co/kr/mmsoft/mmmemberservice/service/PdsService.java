package co.kr.mmsoft.mmmemberservice.service;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Pds;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.PdsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PdsService {

    private final PdsMapper pdsMapper;

    public List<Pds> getList(String category) {
        return pdsMapper.findAll(category);
    }

    /** 다운로드 카운트 증가 후 다운로드 URL 반환 */
    public String download(Integer pdsId) {
        Pds pds = pdsMapper.findById(pdsId);
        if (pds == null) return null;
        pdsMapper.incrementDownloadCount(pdsId);
        return pds.getDownloadUrl();
    }
}
