-- 1. provider 테이블
CREATE TABLE provider (
    provider_id   INT PRIMARY KEY AUTO_INCREMENT,
    provider_name VARCHAR(50) NOT NULL,
    CONSTRAINT uq_provider_name UNIQUE (provider_name)
);

-- 2. account 테이블
CREATE TABLE account (
    account_id  INT PRIMARY KEY AUTO_INCREMENT,
    open_id     VARCHAR(255),
    password    VARCHAR(64),
    name        VARCHAR(25),
    email       VARCHAR(25),
    phone       VARCHAR(25),
    mphone      VARCHAR(25),
    company     VARCHAR(50),
    homepage_id VARCHAR(50),
    provider_id INT,
    CONSTRAINT fk_provider_account FOREIGN KEY (provider_id) REFERENCES provider(provider_id)
);

-- 3. role 테이블
CREATE TABLE role (
    role_id   INT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(15) NOT NULL,
    CONSTRAINT uq_role_name UNIQUE (role_name)
);

-- 4. permission 테이블
CREATE TABLE permission (
    permission_id   INT PRIMARY KEY AUTO_INCREMENT,
    permission_name VARCHAR(25) NOT NULL,
    CONSTRAINT uq_permission_name UNIQUE (permission_name)
);

-- 5. role_permission 테이블
CREATE TABLE role_permission (
    role_permission_id INT PRIMARY KEY AUTO_INCREMENT,
    role_id            INT,
    permission_id      INT,
    CONSTRAINT fk_role_role_permission       FOREIGN KEY (role_id)       REFERENCES role(role_id),
    CONSTRAINT fk_permission_role_permission FOREIGN KEY (permission_id) REFERENCES permission(permission_id)
);

-- 6. account_role 테이블
CREATE TABLE account_role (
    account_role_id INT PRIMARY KEY AUTO_INCREMENT,
    account_id      INT,
    role_id         INT
);

-- 7. freeboard 테이블 (is_secret 포함)
CREATE TABLE freeboard (
    freeboard_id       INT AUTO_INCREMENT PRIMARY KEY COMMENT '게시판 ID (자동증가)',
    account_id         INT                            COMMENT '작성자 회원번호 (FK, 비로그인 허용 시 NULL)',
    title              VARCHAR(255) NOT NULL           COMMENT '제목',
    content            TEXT         NOT NULL           COMMENT '내용',
    name               VARCHAR(50)                     COMMENT '작성자 이름',
    reg_date           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '작성일',
    update_date        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    cnt                INT      DEFAULT 0              COMMENT '조회수',
    ref                INT      NOT NULL DEFAULT 0     COMMENT '글 그룹 (원글번호)',
    step               INT      NOT NULL DEFAULT 0     COMMENT '그룹 내 순서 (댓글 정렬)',
    depth              INT      NOT NULL DEFAULT 0     COMMENT '들여쓰기 단계 (0=원글, 1=댓글)',
    url                VARCHAR(255)                    COMMENT '첨부파일 경로',
    freeboard_rolename VARCHAR(50)  DEFAULT '일반'     COMMENT '게시판 유형 (공지/안내/일반)',
    is_secret          CHAR(1)      DEFAULT 'N'        COMMENT '비밀글 여부 (Y/N)',
    is_deleted         CHAR(1)      DEFAULT 'N'        COMMENT '삭제 여부 (Y/N)',
    CONSTRAINT FK_freeboard_account FOREIGN KEY (account_id)
        REFERENCES account (account_id) ON DELETE SET NULL
) COMMENT '커뮤니티 자유게시판';

-- 8. workboard 테이블
CREATE TABLE workboard (
    workboard_id       INT AUTO_INCREMENT PRIMARY KEY,
    account_id         INT NOT NULL,
    title              VARCHAR(100) DEFAULT NULL,
    content            TEXT         DEFAULT NULL,
    name               VARCHAR(20)  DEFAULT NULL,
    reg_date           DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '작성일',
    update_date        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
    passwd             VARCHAR(20)  DEFAULT NULL COMMENT '열람 패스워드',
    url                VARCHAR(100) DEFAULT NULL,
    workboard_rolename VARCHAR(50)               COMMENT '작업실게시판 종류',
    is_deleted         CHAR(1)      DEFAULT 'N'  COMMENT '삭제 여부 (Y/N)',
    CONSTRAINT FK_workboard_account FOREIGN KEY (account_id)
        REFERENCES account (account_id) ON DELETE CASCADE
);
