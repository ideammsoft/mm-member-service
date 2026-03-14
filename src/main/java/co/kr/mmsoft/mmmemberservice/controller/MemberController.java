package co.kr.mmsoft.mmmemberservice.controller;

import co.kr.mmsoft.mmmemberservice.dto.MemberProfileResponse;
import co.kr.mmsoft.mmmemberservice.dto.MemberProfileUpdateRequest;
import co.kr.mmsoft.mmmemberservice.member.principal.CustomUserDetails;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 회원 정보 CRUD API
 * GET    /api/members/me  - 내 프로필 조회
 * PUT    /api/members/me  - 내 프로필 수정
 * DELETE /api/members/me  - 회원 탈퇴
 *
 * 모든 엔드포인트는 JWT 인증 필요 (Authorization: Bearer 토큰)
 */
@Slf4j
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final AccountMapper accountMapper;

    /** 내 프로필 조회 */
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long accountId = userDetails.getAccount().getAccountId();
        Account account = accountMapper.findByAccountId(accountId);
        if (account == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new MemberProfileResponse(account));
    }

    /** 내 프로필 수정 (phone, email, name, company) */
    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody MemberProfileUpdateRequest request) {

        Long accountId = userDetails.getAccount().getAccountId();
        request.setAccountId(accountId);
        accountMapper.updateProfile(request);
        log.debug("회원 정보 수정 - accountId: {}", accountId);
        return ResponseEntity.ok(Map.of("msg", "ok"));
    }

    /** 회원 탈퇴 */
    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMyAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long accountId = userDetails.getAccount().getAccountId();
        accountMapper.deleteByAccountId(accountId);
        log.debug("회원 탈퇴 - accountId: {}", accountId);
        return ResponseEntity.ok(Map.of("msg", "ok"));
    }
}
