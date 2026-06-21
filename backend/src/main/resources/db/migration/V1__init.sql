-- =========================================================================
--  V1 — 이치 초기 스키마
--  원칙: 일기 "본문"은 DB에 두지 않는다. 본문/미디어는 사용자 Google Drive에,
--        PostgreSQL 에는 인덱싱·정렬·검색용 가벼운 메타데이터(포인터)만 둔다.
-- =========================================================================

-- 사용자 계정 (Google OIDC)
create table users (
    google_sub    text primary key,          -- 구글 OIDC subject
    email         text not null,
    name          text,
    picture_url   text,
    refresh_token text,                       -- Drive 호출용. TOKEN_ENC_KEY 설정 시 AES-GCM 암호화 저장.
    created_at    timestamptz not null default now()
);

-- 일기 메타데이터 (본문은 Drive 의 drive_file_id JSON 에 있음)
create table entries (
    id            uuid primary key,
    user_id       text not null references users(google_sub) on delete cascade,
    drive_file_id text not null,              -- Drive 본문 JSON 파일 ID
    entry_date    date not null,              -- 일기 날짜 (정렬/캘린더용)
    preview       text,                       -- 카드/검색용 앞부분 발췌 (MVP 평문; 향후 암호화/제거 대상)
    pinned        boolean not null default false,
    color         text,                       -- 카드 색 (y/g/b/p/null)
    lat           double precision,           -- 위치(아틀라스용), nullable
    lng           double precision,
    place_name    text,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

create index idx_entries_user_date on entries (user_id, entry_date desc);
create index idx_entries_user_pinned on entries (user_id, pinned);
-- 아틀라스: 위치 있는 항목만
create index idx_entries_user_geo on entries (user_id) where lat is not null and lng is not null;

-- 미디어 메타데이터 (바이너리는 Drive)
create table media (
    id            uuid primary key,
    entry_id      uuid not null references entries(id) on delete cascade,
    drive_file_id text not null,
    thumb_file_id text,                       -- 썸네일 (리스트 빠르게)
    type          text,                       -- image / video
    created_at    timestamptz not null default now()
);

create index idx_media_entry on media (entry_id);
