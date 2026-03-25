package co.kr.mmsoft.mmmemberservice.controller;

import co.kr.mmsoft.mmmemberservice.dto.MemberProfileResponse;
import co.kr.mmsoft.mmmemberservice.dto.MemberProfileUpdateRequest;
import co.kr.mmsoft.mmmemberservice.member.principal.CustomUserDetails;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import co.kr.mmsoft.mmmemberservice.mybatis.mapper.AccountMapper;
import co.kr.mmsoft.mmmemberservice.service.ManymanSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

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

    private final AccountMapper              accountMapper;
    private final Optional<ManymanSyncService> manymanSyncService;
    private final PasswordEncoder            passwordEncoder;

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
        // 1번 테이블(MSSQL manyman) 동기화 (MSSQL 설정이 있을 때만 실행)
        Account account = accountMapper.findByAccountId(accountId);
        manymanSyncService.ifPresent(svc -> svc.syncToManyman(account, request));
        log.debug("회원 정보 수정 - accountId: {}", accountId);
        return ResponseEntity.ok(Map.of("msg", "ok"));
    }

    /** 비밀번호 변경 */
    @PutMapping("/me/password")
    public ResponseEntity<?> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody Map<String, String> body) {

        String currentPassword = body.get("currentPassword");
        String newPassword     = body.get("newPassword");

        if (currentPassword == null || newPassword == null || newPassword.length() < 4) {
            return ResponseEntity.badRequest().body(Map.of("message", "입력값이 올바르지 않습니다."));
        }

        Long accountId = userDetails.getAccount().getAccountId();
        Account account = accountMapper.findByAccountId(accountId);

        if (!passwordEncoder.matches(currentPassword, account.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "현재 비밀번호가 올바르지 않습니다."));
        }

        accountMapper.updatePasswordByAccountId(accountId, passwordEncoder.encode(newPassword));
        log.debug("비밀번호 변경 - accountId: {}", accountId);
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
