-- =========================================================================
--  V3 — appDataFolder → Drive "ichi" 폴더로 변경
--  users 에 ichi_folder_id 추가 (Drive 상의 "ichi" 폴더 ID)
--  null 이면 첫 요청 시 자동 생성
-- =========================================================================

alter table users
    add column ichi_folder_id text;

create index idx_users_ichi_folder_id on users (ichi_folder_id);
