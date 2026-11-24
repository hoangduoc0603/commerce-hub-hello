package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Tiêu chí nghiệm thu 2 của G0-26: toàn bộ migration chạy từ DB trống trên PostgreSQL 17 và 18
// (db-migration.md, mục "Test migration"). Không dựng Spring context: chỉ cần PostgreSQL và Flyway.
class MigrationCompatibilityTests {

    // 17.11: bản minor mới nhất của 17 lúc viết (Docker Hub 2026-11-06). 18.7: cùng image với lab, lấy từ
    // TestcontainersConfiguration để không phải sửa hai chỗ khi nâng.
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"postgres:17.11", TestcontainersConfiguration.POSTGRES_IMAGE})
    void migratesFromEmptyDatabase(String image) throws Exception {
        // try-with-resources: container dừng ngay khi case xong, không chờ tới cuối JVM.
        try (var postgres = new PostgreSQLContainer(image)
                .withDatabaseName("hello")
                // Cùng bốn role như lab, để lỗi quyền (SET ROLE, REVOKE trên database) cũng lộ ra ở cả hai bản.
                .withInitScript("testcontainers/hello-roles.sql")) {
            postgres.start();

            // Lặp lại phần cấu hình Flyway của application.yaml mà có ảnh hưởng tới kết quả SQL. Sửa init-sqls ở
            // đó thì sửa ở đây. Bước 9 có test chạy đúng cấu hình yaml qua profile migrate.
            // transactional-lock không đặt: nó đổi cách khóa, không đổi SQL nên không ảnh hưởng độ tương thích.
            Flyway flyway = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), "hello_migrator", TestcontainersConfiguration.MIGRATOR_PASSWORD)
                    .initSql("SET lock_timeout = '5s'; SET ROLE hello_owner;")
                    .validateMigrationNaming(true)
                    .failOnMissingLocations(true)
                    .load();   // location mặc định classpath:db/migration, giống Boot

            long start = System.nanoTime();
            MigrateResult result = flyway.migrate();
            long millis = (System.nanoTime() - start) / 1_000_000;
            // Số đo cho docs/experiments (bước 11); xem trong báo cáo test, tab "Standard output".
            System.out.printf("G0-26 migration %s: %d migration, %d ms%n", image, result.migrationsExecuted, millis);

            assertThat(result.success).isTrue();
            assertThat(result.migrationsExecuted).isPositive();
            // Checksum trong flyway_schema_history khớp file: bắt trường hợp hai file cùng version.
            flyway.validate();

            // Kiểm bằng superuser "test" của container (createConnection), vì chỉ cần đọc catalog.
            List<String> owners = new ArrayList<>();
            try (var conn = postgres.createConnection("");
                 ResultSet rs = conn.createStatement().executeQuery(
                         "select tablename || '=' || tableowner from pg_tables where schemaname = 'public' order by 1")) {
                while (rs.next()) {
                    owners.add(rs.getString(1));
                }
            }
            assertThat(owners).isNotEmpty().allMatch(o -> o.endsWith("=hello_owner"));
        }
    }
}
