-- Bốn role và database của hello, chép theo gitops/infra/pg-main/hello.yaml (CR DatabaseRole, Database).
-- Sửa file đó thì sửa file này cùng lúc: test chỉ có giá trị khi quyền giống lab.
-- Testcontainers chạy script một lần, bằng user superuser "test" của container, trên database "hello".
-- Mật khẩu là giá trị chỉ dùng cho test (lab dùng SealedSecret).

-- Owner schema và bảng. NOLOGIN: không ai đăng nhập bằng role này; migration làm việc bằng SET ROLE hello_owner.
CREATE ROLE hello_owner NOLOGIN;

-- Chỉ sở hữu hàm SECURITY DEFINER tra cứu (G1). BYPASSRLS chỉ superuser cấp được, ở lab qua CR.
CREATE ROLE hello_lookup NOLOGIN BYPASSRLS;

-- Job Flyway (bước 3). IN ROLE = inRoles của CR: là thành viên nên SET ROLE hello_owner được.
-- CONNECTION LIMIT giống lab: Flyway 1 kết nối, chừa 1 cho Job chồng nhau.
CREATE ROLE hello_migrator LOGIN PASSWORD 'hello_migrator_test' CONNECTION LIMIT 2 IN ROLE hello_owner, hello_lookup;

-- Runtime. Không thuộc role nào ở trên: chỉ có quyền DML mà migration cấp (bước 3).
CREATE ROLE hello_app LOGIN PASSWORD 'hello_app_test' CONNECTION LIMIT 10;

-- CR Database tạo database với owner hello_owner. PostgreSQL 15+: schema public thuộc pg_database_owner,
-- nên đổi owner database là hello_owner tạo được bảng trong public, còn hello_app thì không.
ALTER DATABASE hello OWNER TO hello_owner;