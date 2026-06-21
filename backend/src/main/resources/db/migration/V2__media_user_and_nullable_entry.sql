-- =========================================================================
--  V2 — 본문 인라인 이미지(M3) 지원
--  작성 중(아직 entry 없음)에 업로드한 미디어를 담기 위해:
--   - media.user_id 추가 (소유자 직접 참조)
--   - media.entry_id 를 nullable 로 (작성 완료 시 연결)
-- =========================================================================

alter table media
    add column user_id text references users(google_sub) on delete cascade;

-- 기존 행이 있다면 entry 의 소유자로 채움 (개발 초기엔 비어 있을 것)
update media m
   set user_id = e.user_id
  from entries e
 where m.entry_id = e.id
   and m.user_id is null;

alter table media alter column entry_id drop not null;

create index idx_media_user on media (user_id);
