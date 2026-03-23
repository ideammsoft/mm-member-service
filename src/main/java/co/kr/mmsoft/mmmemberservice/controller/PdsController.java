package co.kr.mmsoft.mmmemberservice.controller;

import co.kr.mmsoft.mmmemberservice.mybatis.domain.Pds;
import co.kr.mmsoft.mmmemberservice.service.PdsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 제품소개 공개 API
 * GET  /api/pds?category=software  → 목록 조회
 * POST /api/pds/{id}/download      → 다운로드 수 증가 + URL 반환
 */
@RestController
@RequestMapping("/api/pds")
@RequiredArgsConstructor
public class PdsController {

    private final PdsService pdsService;

    /** 목록 조회 - 카테고리 필터 */
    @GetMapping
    public ResponseEntity<List<Pds>> getList(@RequestParam(required = false) String category) {
        return ResponseEntity.ok(pdsService.getList(category));
    }

    /** 다운로드 클릭 - 카운트 증가 후 URL 반환 */
    @PostMapping("/{id}/download")
    public ResponseEntity<?> download(@PathVariable Integer id) {
        String url = pdsService.download(id);
        if (url == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("url", url));
    }
}
