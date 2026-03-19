-- seed 데이터 중복 정리 및 UNIQUE 제약 추가

-- 1. role_permission 중복 제거 (role/permission 정리 전에 먼저)
DELETE FROM role_permission
WHERE role_permission_id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(role_permission_id) AS min_id
        FROM role_permission
        GROUP BY role_id, permission_id
    ) AS t
);

-- 2. role_permission에서 중복 role_id 참조 제거 (삭제될 role_id를 참조하는 행 제거)
DELETE FROM role_permission
WHERE role_id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(role_id) AS min_id FROM role GROUP BY role_name
    ) AS t
);

DELETE FROM role_permission
WHERE permission_id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(permission_id) AS min_id FROM permission GROUP BY permission_name
    ) AS t
);

-- 3. account_role에서 중복 role_id 참조 제거
DELETE FROM account_role
WHERE role_id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(role_id) AS min_id FROM role GROUP BY role_name
    ) AS t
);

-- 4. account에서 중복 provider_id 참조 제거 (중복 provider 삭제 전)
UPDATE account
SET provider_id = (
    SELECT min_id FROM (
        SELECT MIN(provider_id) AS min_id FROM provider
        WHERE provider_name = (SELECT provider_name FROM provider WHERE provider_id = account.provider_id)
    ) AS t
)
WHERE provider_id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(provider_id) AS min_id FROM provider GROUP BY provider_name
    ) AS t
);

-- 5. provider 중복 제거
DELETE FROM provider
WHERE provider_id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(provider_id) AS min_id FROM provider GROUP BY provider_name
    ) AS t
);

-- 6. role 중복 제거
DELETE FROM role
WHERE role_id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(role_id) AS min_id FROM role GROUP BY role_name
    ) AS t
);

-- 7. permission 중복 제거
DELETE FROM permission
WHERE permission_id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(permission_id) AS min_id FROM permission GROUP BY permission_name
    ) AS t
);

-- 8. UNIQUE 제약 추가 (재발 방지)
ALTER TABLE provider   ADD CONSTRAINT uq_provider_name   UNIQUE (provider_name);
ALTER TABLE role       ADD CONSTRAINT uq_role_name       UNIQUE (role_name);
ALTER TABLE permission ADD CONSTRAINT uq_permission_name UNIQUE (permission_name);
