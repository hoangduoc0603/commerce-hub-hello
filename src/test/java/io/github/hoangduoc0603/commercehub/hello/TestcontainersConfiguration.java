package io.github.hoangduoc0603.commercehub.hello;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

// Hạ tầng DB cho test và cho bootTestRun (bước 8): PostgreSQL và PgBouncer transaction, giống pg-main trên lab.
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    // Mật khẩu test, khớp hello-roles.sql và userlist.txt. Bước 3 thêm MIGRATOR_PASSWORD khi Flyway cần.
    static final String APP_PASSWORD = "hello_app_test";

    // Cùng major.minor với lab: gitops/infra/pg-main/cluster.yaml (imageName postgresql:18.7-...).
    // Nâng PostgreSQL trên lab thì đổi tag này cùng lúc. Image chính thức "postgres", không phải image CNPG:
    // PostgreSQLContainer chỉ nhận image tương thích với "postgres" và dựa vào entrypoint của image chính thức
    // (biến POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB).
    // Chỉ ghim tag, không ghim digest: DockerImageName của Testcontainers 2.0.5 với "tag@sha256:..." đưa ":tag"
    // vào tên repository, nên PostgreSQLContainer coi image là không tương thích với "postgres" (đọc mã nguồn).
    static final String POSTGRES_IMAGE = "postgres:18.7";

    // Đúng image của Pooler lab: gitops/infra/pg-main/pooler.yaml (spec.pgbouncer.image), cả tag lẫn digest.
    // GenericContainer không kiểm tương thích, nên tag@digest chạy được (đã thử 2026-11-25).
    static final String PGBOUNCER_IMAGE =
            "ghcr.io/cloudnative-pg/pgbouncer:1.25.2-202609221033-trixie@sha256:b15c428057d1ffbc19fb70496911a5f1a47066dcc1315bf794f4e85ade29e2e5";
    // Khớp listen_port trong pgbouncer.ini.
    static final int PGBOUNCER_PORT = 6432;

    // Network riêng để PgBouncer gọi PostgreSQL bằng tên "postgres", không qua cổng của máy.
    // Network là AutoCloseable: Spring gọi close() khi context đóng, sau khi đã dừng các container dùng nó.
    @Bean
    Network network() {
        return Network.newNetwork();
    }

    // Spring khởi động container khi bean được tạo và dừng nó khi context đóng.
    @Bean
    PostgreSQLContainer postgres(Network network) {
        return new PostgreSQLContainer(POSTGRES_IMAGE)
                .withNetwork(network)
                // Tên mà pgbouncer.ini dùng ở [databases] host=postgres.
                .withNetworkAliases("postgres")
                // Tên database giống lab (CR Database "hello"), để script ALTER DATABASE hello chạy được.
                .withDatabaseName("hello")
                // Chạy sau khi DB sẵn sàng, bằng superuser "test". Đường dẫn tính từ classpath.
                .withInitScript("testcontainers/hello-roles.sql");
    }

    @Bean
    GenericContainer<?> pgbouncer(Network network, PostgreSQLContainer postgres) {
        return new GenericContainer<>(PGBOUNCER_IMAGE)
                .withNetwork(network)
                // Testcontainers khởi động postgres trước PgBouncer, để kết nối đầu tiên không gặp lúc DB chưa sẵn sàng.
                .dependsOn(postgres)
                // Image chạy "pgbouncer /etc/pgbouncer/pgbouncer.ini" bằng user pgbouncer (uid 998), không phải root:
                // file phải đọc được bởi người khác chủ (0644).
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("testcontainers/pgbouncer.ini", 0644),
                        "/etc/pgbouncer/pgbouncer.ini")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("testcontainers/userlist.txt", 0644),
                        "/etc/pgbouncer/userlist.txt")
                // Mở cổng ra máy. Khi có cổng mở, Testcontainers mặc định chờ cổng đó nhận kết nối rồi mới coi là đã chạy.
                .withExposedPorts(PGBOUNCER_PORT);
    }

    // Không dùng @ServiceConnection: nó sẽ cho DataSource đăng nhập bằng user "test", là superuser, nên
    // bỏ qua mọi kiểm quyền. Test sẽ xanh cả ở những chỗ mà lab báo "permission denied".
    // DataSource (runtime) đi qua PgBouncer bằng hello_app, giống pod trên lab.
    // Bước 3 thêm spring.flyway.* nối thẳng postgres bằng hello_migrator.
    @Bean
    DynamicPropertyRegistrar databaseProperties(GenericContainer<?> pgbouncer) {
        return registry -> {
            // getHost() và getMappedPort() đọc lúc Spring cần giá trị, khi container đã chạy.
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/hello"
                    .formatted(pgbouncer.getHost(), pgbouncer.getMappedPort(PGBOUNCER_PORT)));
            registry.add("spring.datasource.username", () -> "hello_app");
            registry.add("spring.datasource.password", () -> APP_PASSWORD);
        };
    }
}
