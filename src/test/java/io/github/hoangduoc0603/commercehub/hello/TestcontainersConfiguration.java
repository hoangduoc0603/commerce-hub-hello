package io.github.hoangduoc0603.commercehub.hello;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * @author duochv on 11/24/26 23:47
 */

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {
    // Mật khẩu test, khớp hello-roles.sql. Bước 3 thêm MIGRATOR_PASSWORD khi Flyway cần.
    static final String APP_PASSWORD = "hello_app_test";

    // Cùng major.minor với lab: gitops/infra/pg-main/cluster.yaml (imageName postgresql:18.7-...).
    // Nâng PostgreSQL trên lab thì đổi tag này cùng lúc. Image chính thức "postgres", không phải image CNPG:
    // PostgreSQLContainer chỉ nhận image tương thích với "postgres" và dựa vào entrypoint của image chính thức
    // (biến POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB).
    static final String POSTGRES_IMAGE = "postgres:18.7";

    // Spring khởi động container khi bean được tạo và dừng nó khi context đóng.
    @Bean
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                // Tên database giống lab (CR Database "hello"), để script ALTER DATABASE hello chạy được.
                .withDatabaseName("hello")
                // Chạy sau khi DB sẵn sàng, bằng superuser "test". Đường dẫn tính từ classpath.
                .withInitScript("testcontainers/hello-roles.sql");
    }

    // Không dùng @ServiceConnection: nó sẽ cho DataSource đăng nhập bằng user "test", là superuser, nên
    // bỏ qua mọi kiểm quyền. Test sẽ xanh cả ở những chỗ mà lab báo "permission denied".
    // Tham số `postgres` bảo đảm container đã chạy trước khi đọc cổng (docs Boot, "Dynamic Properties").
    @Bean
    DynamicPropertyRegistrar databaseProperties(PostgreSQLContainer postgres) {
        return registry -> {
            // Bước 1: nối thẳng PostgreSQL. Bước 2 đổi URL này sang PgBouncer.
            // Dùng method reference (Supplier): giá trị đọc lúc cần, không đọc lúc khai báo.
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", () -> "hello_app");
            registry.add("spring.datasource.password", () -> APP_PASSWORD);
        };
    }
}
