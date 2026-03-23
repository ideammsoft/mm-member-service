package co.kr.mmsoft.mmmemberservice.service;

import co.kr.mmsoft.mmmemberservice.dto.MemberProfileUpdateRequest;
import co.kr.mmsoft.mmmemberservice.dto.RegistRequest;
import co.kr.mmsoft.mmmemberservice.mybatis.domain.Account;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

/**
 * MSSQL manymen_bank.dbo.manyman 테이블 동기화 서비스
 * MySQL account 변경 → MSSQL manyman 테이블 UPSERT
 *
 * [일반 로그인] account.open_id = manyman.id → UPDATE or INSERT
 * [OAuth 로그인] account.email  = manyman.id → UPDATE or INSERT(password 빈칸)
 *
 * 암호화: dbo.AES_Encript(password, '1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ')
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.mssql-manyman", name = "jdbc-url")
public class ManymanSyncService {

    private static final String AES_KEY =
            "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final DataSource mssqlDataSource;

    // @Qualifier가 Lombok @RequiredArgsConstructor에서 생성자 파라미터로 복사되지 않으므로
    // 명시적 생성자에서 @Qualifier 지정
    public ManymanSyncService(@Qualifier("mssqlManymanDataSource") DataSource mssqlDataSource) {
        this.mssqlDataSource = mssqlDataSource;
    }

    /**
     * 회원가입 시 manyman 테이블 동기화 (homepage 가입만 해당)
     *
     * @param req         가입 요청 데이터 (openId, name, email, phone, mphone, company)
     * @param rawPassword 평문 비밀번호 (AES 암호화 후 manyman.password 에 저장)
     */
    public void syncOnRegist(RegistRequest req, String rawPassword) {
        if (req.getOpenId() == null || req.getOpenId().isBlank()) return;
        MemberProfileUpdateRequest updateReq = toUpdateReq(req);
        try (Connection conn = mssqlDataSource.getConnection()) {
            if (existsById(conn, req.getOpenId())) {
                update(conn, req.getOpenId(), updateReq);
            } else {
                insertWithPassword(conn, req.getOpenId(), rawPassword, updateReq);
            }
        } catch (Exception e) {
            log.warn("manyman 동기화 실패(회원가입) - openId: {}, 오류: {}", req.getOpenId(), e.getMessage());
        }
    }

    /** RegistRequest → MemberProfileUpdateRequest 변환 */
    private MemberProfileUpdateRequest toUpdateReq(RegistRequest req) {
        MemberProfileUpdateRequest r = new MemberProfileUpdateRequest();
        r.setName(req.getName());
        r.setEmail(req.getEmail());
        r.setPhone(req.getPhone());
        r.setMphone(req.getMphone());
        r.setCompany(req.getCompany());
        return r;
    }

    /**
     * 회원 정보 수정 시 manyman 테이블 동기화 (UPSERT)
     *
     * @param account 현재 로그인 계정 (providerName으로 OAuth 여부 판단)
     * @param req     수정 요청 데이터 (name, email, phone, mphone, company)
     */
    public void syncToManyman(Account account, MemberProfileUpdateRequest req) {
        boolean isOAuth = account.getProvider() != null
                && !"homepage".equals(account.getProvider().getProviderName());

        if (isOAuth) {
            syncOAuth(account, req);
        } else {
            syncHomepage(account.getOpenId(), account.getPassword(), req);
        }
    }

    /** 일반(homepage) 로그인: open_id = manyman.id 로 UPSERT */
    private void syncHomepage(String openId, String rawPassword, MemberProfileUpdateRequest req) {
        try (Connection conn = mssqlDataSource.getConnection()) {
            if (existsById(conn, openId)) {
                update(conn, openId, req);
            } else {
                insertWithPassword(conn, openId, rawPassword, req);
            }
        } catch (Exception e) {
            log.warn("manyman 동기화 실패(homepage) - openId: {}, 오류: {}", openId, e.getMessage());
        }
    }

    /** OAuth 로그인: account.email = manyman.id 로 UPSERT */
    private void syncOAuth(Account account, MemberProfileUpdateRequest req) {
        String email = account.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("manyman 동기화 스킵 - OAuth 계정에 email 없음");
            return;
        }
        try (Connection conn = mssqlDataSource.getConnection()) {
            if (existsById(conn, email)) {
                update(conn, email, req);
            } else {
                insertWithEmptyPassword(conn, email, req);
            }
        } catch (Exception e) {
            log.warn("manyman 동기화 실패(OAuth) - email: {}, 오류: {}", email, e.getMessage());
        }
    }

    /** manyman.id 존재 여부 확인 */
    private boolean existsById(Connection conn, String id) throws Exception {
        String sql = "SELECT COUNT(*) FROM manyman WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /** 5개 항목 UPDATE */
    private void update(Connection conn, String id, MemberProfileUpdateRequest req) throws Exception {
        String sql = """
                UPDATE manyman SET
                    name     = ?,
                    email    = ?,
                    phone    = ?,
                    mphone   = ?,
                    company  = ?,
                    editdate = CONVERT(NVARCHAR(10), GETDATE(), 23)
                WHERE id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, req.getName());
            ps.setString(2, req.getEmail());
            ps.setString(3, req.getPhone());
            ps.setString(4, req.getMphone());
            ps.setString(5, req.getCompany());
            ps.setString(6, id);
            int rows = ps.executeUpdate();
            log.info("manyman UPDATE 완료 - id: {}, 행수: {}", id, rows);
        }
    }

    /** INSERT (일반 로그인 - 암호화된 password 포함) */
    private void insertWithPassword(Connection conn, String id, String rawPassword,
                                    MemberProfileUpdateRequest req) throws Exception {
        String sql = """
                INSERT INTO manyman (id, passwd, payment, version, name, email, phone, mphone, company, password, editdate)
                VALUES (?, '', 0, '', ?, ?, ?, ?, ?, dbo.AES_Encript(?, ?), CONVERT(NVARCHAR(10), GETDATE(), 23))
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, req.getName());
            ps.setString(3, req.getEmail());
            ps.setString(4, req.getPhone());
            ps.setString(5, req.getMphone());
            ps.setString(6, req.getCompany());
            ps.setString(7, rawPassword != null ? rawPassword : "");
            ps.setString(8, AES_KEY);
            int rows = ps.executeUpdate();
            log.info("manyman INSERT 완료(homepage) - id: {}, 행수: {}", id, rows);
        }
    }

    /**
     * OAuth 최초 로그인 시 manyman 테이블에 없으면 INSERT
     * 전화/회사 정보는 빈칸으로 저장 (이후 프로필 수정 시 채워짐)
     *
     * @param email OAuth 이메일 (manyman.id 로 사용)
     * @param name  OAuth 이름
     */
    public void syncOAuthOnLogin(String email, String name) {
        if (email == null || email.isBlank()) {
            log.warn("manyman 로그인 동기화 스킵 - email 없음");
            return;
        }
        try (Connection conn = mssqlDataSource.getConnection()) {
            if (!existsById(conn, email)) {
                String sql = """
                        INSERT INTO manyman (id, passwd, payment, version, name, email, phone, mphone, company, password, editdate)
                        VALUES (?, '', 0, '', ?, ?, '', '', '', '', CONVERT(NVARCHAR(10), GETDATE(), 23))
                        """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, email);
                    ps.setString(2, name != null ? name : "");
                    ps.setString(3, email);
                    int rows = ps.executeUpdate();
                    log.info("manyman INSERT 완료(OAuth 최초 로그인) - email: {}, 행수: {}", email, rows);
                }
            } else {
                log.debug("manyman 동기화 스킵(OAuth 로그인) - 이미 존재: {}", email);
            }
        } catch (Exception e) {
            log.warn("manyman 동기화 실패(OAuth 로그인) - email: {}, 오류: {}", email, e.getMessage());
        }
    }

    /**
     * 아이디 + 휴대폰번호로 manyman 조회 (비밀번호 찾기 fallback용)
     * 전화번호 하이픈/공백 자동 정규화
     *
     * @return 사용자 정보 Map(name, email, mphone, phone, company) 또는 null
     */
    public Map<String, String> findByOpenIdAndPhone(String openId, String phone) {
        if (openId == null || phone == null) return null;
        String normalized = phone.replaceAll("[^0-9]", "");
        String sql = """
                SELECT TOP 1 name, email, mphone, phone, company
                FROM manyman
                WHERE id = ?
                  AND REPLACE(REPLACE(mphone, '-', ''), ' ', '') = ?
                  AND isDelete = 0
                """;
        try (Connection conn = mssqlDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, openId);
            ps.setString(2, normalized);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, String> result = new HashMap<>();
                    result.put("name",    rs.getString("name"));
                    result.put("email",   rs.getString("email"));
                    result.put("mphone",  rs.getString("mphone"));
                    result.put("phone",   rs.getString("phone"));
                    result.put("company", rs.getString("company"));
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("manyman 조회 실패 - openId: {}, 오류: {}", openId, e.getMessage());
        }
        return null;
    }

    /** INSERT (OAuth 로그인 - password 빈칸) */
    private void insertWithEmptyPassword(Connection conn, String id,
                                         MemberProfileUpdateRequest req) throws Exception {
        String sql = """
                INSERT INTO manyman (id, passwd, payment, version, name, email, phone, mphone, company, password, editdate)
                VALUES (?, '', 0, '', ?, ?, ?, ?, ?, '', CONVERT(NVARCHAR(10), GETDATE(), 23))
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, req.getName());
            ps.setString(3, req.getEmail());
            ps.setString(4, req.getPhone());
            ps.setString(5, req.getMphone());
            ps.setString(6, req.getCompany());
            int rows = ps.executeUpdate();
            log.info("manyman INSERT 완료(OAuth) - id: {}, 행수: {}", id, rows);
        }
    }
}
