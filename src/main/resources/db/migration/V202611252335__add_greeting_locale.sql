-- G0-27 bước 8: migration LỖI CỐ Ý để thử AC2 (PreSync chặn bản lỗi). Sẽ được gỡ khỏi main sau thí nghiệm.
-- Lỗi: NOT NULL và DEFAULT viết thành hai lệnh. Bảng trống (CI) qua được; bảng có dữ liệu (lab) lỗi
-- "column "locale" of relation "greeting" contains null values". Đúng là một lệnh:
--   ALTER TABLE greeting ADD COLUMN locale text NOT NULL DEFAULT 'vi';
ALTER TABLE greeting ADD COLUMN locale text NOT NULL;
ALTER TABLE greeting ALTER COLUMN locale SET DEFAULT 'vi';
