-- G0-26: lời chào của hello. Bảng toàn cục, không có workspace_id: hello không thuộc Workspace nào (spec G0), nên
-- không có RLS (sang G1 cùng ticket 25).
-- Chạy bằng hello_migrator sau SET ROLE hello_owner (spring.flyway.init-sqls): mọi object thuộc hello_owner.
-- Migration đã merge thì KHÔNG sửa: muốn đổi thì thêm file mới (db-migration.md, mục "Bất biến sau khi merge").

-- Quyền cho runtime: bảng và sequence mà hello_owner tạo từ nay về sau tự có quyền cho hello_app, nên migration
-- sau không cần nhớ GRANT. Chỉ DML: hello_app không TRUNCATE, không DDL.
ALTER DEFAULT PRIVILEGES FOR ROLE hello_owner
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO hello_app;
ALTER DEFAULT PRIVILEGES FOR ROLE hello_owner
    GRANT USAGE, SELECT ON SEQUENCES TO hello_app;

-- CR Database của CNPG không quản lý quyền, nên PUBLIC giữ quyền TEMPORARY mặc định. Runtime không cần bảng tạm.
-- Chỉ owner database thu hồi được, nên lệnh này chạy dưới SET ROLE hello_owner.
REVOKE TEMPORARY ON DATABASE hello FROM PUBLIC;

CREATE TABLE greeting (
    -- UUIDv7 sinh trong app (bước 5). Không DEFAULT uuidv7(): PostgreSQL 17 không có hàm này, và spec muốn ID
    -- sinh trong service.
    id         uuid        PRIMARY KEY,
    -- 1 tới 50 ký tự, cùng luật với Bean Validation (bước 7). Ràng buộc trong DB là lớp chặn cuối cùng.
    -- char_length đếm ký tự, nên "Đức" là 3.
    name       text        NOT NULL CHECK (char_length(name) BETWEEN 1 AND 50),
    created_at timestamptz NOT NULL DEFAULT now()
);
