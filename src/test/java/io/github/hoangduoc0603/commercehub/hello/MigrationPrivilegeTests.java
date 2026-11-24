package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

// Tách quyền migrate và runtime (db-migration.md, mục "Tách quyền migrate và runtime"). Mọi truy vấn chạy bằng
// hello_app qua PgBouncer, đúng như pod runtime trên lab.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MigrationPrivilegeTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    void migratedTablesAreOwnedByHelloOwner() {
        // Cả flyway_schema_history cũng phải thuộc hello_owner: chứng minh init-sqls (SET ROLE) chạy trước khi
        // Flyway tạo bảng lịch sử. db-migration.md đang ghi điểm này là "chưa xác minh".
        var owners = jdbc.sql("""
                select tablename || '=' || tableowner from pg_tables
                where schemaname = 'public' order by tablename""").query(String.class).list();
        assertThat(owners).containsExactly("flyway_schema_history=hello_owner", "greeting=hello_owner");
    }

    @Test
    void appHasDmlOnGreetingButNotOnHistory() {
        // Quyền đến từ ALTER DEFAULT PRIVILEGES trong migration.
        assertThat(privilege("greeting", "SELECT, INSERT, UPDATE, DELETE")).isTrue();
        assertThat(privilege("greeting", "TRUNCATE")).isFalse();
        // flyway_schema_history được tạo TRƯỚC lệnh ALTER DEFAULT PRIVILEGES, nên hello_app không ghi được vào lịch sử.
        assertThat(privilege("flyway_schema_history", "INSERT")).isFalse();
    }

    @Test
    void appCannotCreateTable() {
        // PostgreSQL 15+ đã bỏ quyền CREATE trên schema public của PUBLIC. Test này giữ mặc định đó không bị đổi.
        // rootCause(): Spring bọc lỗi trong BadSqlGrammarException, câu thông báo gốc nằm ở PSQLException bên trong.
        assertThatThrownBy(() -> jdbc.sql("create table hijack (id int)").update())
                .rootCause()
                .hasMessageContaining("permission denied for schema public");
    }

    @Test
    void appCannotCreateTempTable() {
        // Nhờ REVOKE TEMPORARY trong migration. Không có lệnh đó, PUBLIC (nên cả hello_app) tạo được bảng tạm.
        assertThatThrownBy(() -> jdbc.sql("create temp table scratch (id int)").update())
                .rootCause()
                .hasMessageContaining("permission denied to create temporary tables");
    }

    private boolean privilege(String table, String privileges) {
        return jdbc.sql("select has_table_privilege(:table, :privileges)")
                .param("table", table)
                .param("privileges", privileges)
                .query(Boolean.class)
                .single();
    }
}
