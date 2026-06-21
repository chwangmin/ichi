-- =========================================================================
--  V4 — Drive 폴더 계층화 (ichi/YYYY-MM-DD/entryId_preview/)
--  entries 에 entry_folder_id 추가 (Drive 상의 entryId 폴더 ID)
-- =========================================================================

alter table entries
    add column entry_folder_id text;

create index idx_entries_entry_folder_id on entries (entry_folder_id);
