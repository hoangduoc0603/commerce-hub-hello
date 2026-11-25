package io.github.hoangduoc0603.commercehub.hello;

import org.springframework.boot.SpringApplication;

// Chạy hello trên máy với PostgreSQL 18.7 và PgBouncer transaction của Testcontainers (G0-26, tiêu chí 5):
//   ./gradlew bootTestRun
// Dùng lại đúng TestcontainersConfiguration của test, nên máy dev, test và lab cùng một mô hình kết nối:
// runtime là hello_app qua PgBouncer, Flyway là hello_migrator nối thẳng.
// Phải nằm trong src/test: classpath main không có Testcontainers, nên image production không chứa class này.
// DB mới tinh mỗi lần chạy: dừng app là container bị xóa, dữ liệu mất. Cần dữ liệu giữ lâu thì dùng compose (ticket 32).
public class TestHelloApplication {

    public static void main(String[] args) {
        // from(...): gọi main của app thật; with(...): nạp thêm @TestConfiguration chứa container.
        SpringApplication.from(HelloApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
