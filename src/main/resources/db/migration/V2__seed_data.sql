/*Provider 데이터*/
insert into provider(provider_name) values('google');
insert into provider(provider_name) values('naver');
insert into provider(provider_name) values('kakao');
insert into provider(provider_name) values('homepage');
/*Role 데이터*/
insert into role(role_name) values('super_admin');
insert into role(role_name) values('admin'); /*staff*/
insert into role(role_name) values('customer');

/*권한을 부여할 필요없는 공개 기능의 경우는 INSERT 하지 말자 @PreAuthorize 대상이 되지 않음..*/
insert into permission(permission_name) values('PRODUCT_CREATE');
insert into permission(permission_name) values('PRODUCT_UPDATE');
insert into permission(permission_name) values('PRODUCT_DELETE');
insert into permission(permission_name) values('NOTICE_CREATE');
insert into permission(permission_name) values('NOTICE_UPDATE');
insert into permission(permission_name) values('NOTICE_DELETE');
insert into permission(permission_name) values('NOTICE_VIEW');

/*
 최고 권한 롤에 권한 부여하기
 */
insert into role_permission(role_id, permission_id) /* values(1,1)*/
select 1, permission_id from permission;

/*관리자 직원 롤에 권한 부여하기 */
insert into role_permission(role_id, permission_id) /* values(2,1)*/
select 2, permission_id from permission
where permission_name like 'NOTICE_%';

/*고객롤에게 특정 권한 부여 (role_id: 3)*/
insert into role_permission(role_id, permission_id)
select 3, permission_id from permission
where permission_name = 'NOTICE_VIEW';
