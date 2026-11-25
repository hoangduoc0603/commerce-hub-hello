package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;

// Tiêu chí nghiệm thu 3 của G0-26: profile migrate chạy Flyway và không mở cổng HTTP. Đây là chế độ của Job PreSync
// (ticket 27). Mã thoát của process thật kiểm bằng tay (ticket G0-26, bước 9), vì test chạy trong JVM của Gradle.
class MigrateProfileTests {

    @Test
    void migrateProfileRunsFlywayWithoutWebServer() throws Exception {
        // DB riêng, chỉ có PostgreSQL: Job trên lab nối thẳng pg-main-rw, không qua PgBouncer.
        try (var postgres = new PostgreSQLContainer(TestcontainersConfiguration.POSTGRES_IMAGE)
                .withDatabaseName("hello")
                .withInitScript("testcontainers/hello-roles.sql")) {
            postgres.start();

            // Khởi động app như Job sẽ làm: profile migrate, chỉ truyền spring.flyway.* (Job ở ticket 27 truyền qua env
            // SPRING_FLYWAY_URL/USER/PASSWORD). Tham số dòng lệnh đè mọi file cấu hình.
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(HelloApplication.class)
                    .profiles("migrate")
                    .run("--spring.flyway.url=" + postgres.getJdbcUrl(),
                         "--spring.flyway.user=hello_migrator",
                         "--spring.flyway.password=" + TestcontainersConfiguration.MIGRATOR_PASSWORD)) {
                // Có web server thì context là WebServerApplicationContext. Không phải nghĩa là không mở cổng nào,
                // kể cả cổng Actuator 8081 (management server chỉ chạy khi có web server).
                assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
            }

            // Flyway đã chạy: đọc lịch sử bằng superuser "test" của container.
            try (var conn = postgres.createConnection("");
                 ResultSet rs = conn.createStatement().executeQuery(
                         "select count(*) from flyway_schema_history where success")) {
                rs.next();
                assertThat(rs.getInt(1)).isPositive();
            }
        }
    }
}
