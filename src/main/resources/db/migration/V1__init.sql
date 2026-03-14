-- 1. provider 테이블
create table provider (
                          provider_id int primary key auto_increment,
                          provider_name varchar(50) not null
);

/*---------------------------------------------
로그인 주체 (최고관리자, 관리자, 고객)
---------------------------------------------*/

create table account(
    account_id int primary key auto_increment
    , open_id varchar(255)
    , password varchar(64)
    , name varchar(25)
    , email varchar(25)
    , phone varchar(25)
    , company varchar(50)
    , provider_id int
    , constraint fk_provider_account foreign key (provider_id) references provider(provider_id)
);

-- 2. role 테이블 (여기에 'table'이 빠졌을 가능성이 큽니다)
create table role (
      role_id int primary key auto_increment,
      role_name varchar(15) not null
);

-- 3. permission 테이블
create table permission (
    permission_id int primary key auto_increment,
    permission_name varchar(25) not null
);

-- 4. role_permission 테이블
create table role_permission (
                                 role_permission_id int primary key auto_increment,
                                 role_id int,
                                 permission_id int,
                                 constraint fk_role_role_permission foreign key (role_id) references role(role_id),
                                 constraint fk_permission_role_permission foreign key (permission_id) references permission(permission_id)
);

-- 2. role 테이블 (여기에 'table'이 빠졌을 가능성이 큽니다)
create table account_role (
      account_role_id int primary key auto_increment,
      account_id int,
      role_id int
);
--  자유게시판 테이블 생성
CREATE TABLE freeboard (
       freeboard_id     INT AUTO_INCREMENT PRIMARY KEY COMMENT '게시판 ID (자동증가)',
       account_id       INT NOT NULL                COMMENT '작성자 회원번호 (FK)',
       title            VARCHAR(255) NOT NULL          COMMENT '제목',
       content          TEXT NOT NULL                  COMMENT '내용',
       name             VARCHAR(50)                    COMMENT '작성자 이름(작성시점)',
       reg_date         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '작성일',
       update_date      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
       cnt              INT DEFAULT 0                  COMMENT '조회수',
       ref              INT NOT NULL DEFAULT 0         COMMENT '글 그룹(원글번호)',
       step             INT NOT NULL DEFAULT 0         COMMENT '그룹 내 순서',
       depth            INT NOT NULL DEFAULT 0         COMMENT '들여쓰기 단계',
       url              VARCHAR(100)                    COMMENT '파일경로',
       freeboard_rolename  VARCHAR(50)                    COMMENT '게시판 종류',
       is_deleted       CHAR(1) DEFAULT 'N'            COMMENT '삭제 여부 (Y/N)',
       CONSTRAINT FK_freeboard_account FOREIGN KEY (account_id)
           REFERENCES account (account_id) ON DELETE CASCADE
); -- 세미콜론 추가

CREATE TABLE workboard (
       workboard_id     INT AUTO_INCREMENT PRIMARY KEY,
       account_id       INT NOT NULL,
       title            VARCHAR(100) DEFAULT NULL,
       content          TEXT DEFAULT NULL,
       name             VARCHAR(20) DEFAULT NULL,
       reg_date         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '작성일',
       update_date      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일',
       passwd           VARCHAR(20) DEFAULT NULL COMMENT '열람 패스워드', -- 쉼표 오류 수정
       url              VARCHAR(100) DEFAULT NULL,
       workboard_rolename VARCHAR(50)                    COMMENT '작업실게시판 종류',
       is_deleted       CHAR(1) DEFAULT 'N'            COMMENT '삭제 여부 (Y/N)',
       CONSTRAINT FK_workboard_account FOREIGN KEY (account_id) -- 제약 조건 이름 중복 수정
           REFERENCES account (account_id) ON DELETE CASCADE
);