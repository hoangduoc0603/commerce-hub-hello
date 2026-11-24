package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

// Cùng cấu hình với HelloApplicationTests (MOCK + cùng @Import), nên Spring dùng chung context và container.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DatabaseConnectionTests {

    // Boot tự tạo bean JdbcClient từ DataSource khi có starter JDBC.
    @Autowired
    JdbcClient jdbc;

    @Test
    void runtimeConnectsAsHelloApp() {
        // Runtime phải là hello_app, không phải superuser: mọi kiểm quyền ở bước 3 dựa vào điều này.
        String user = jdbc.sql("select current_user").query(String.class).single();
        assertThat(user).isEqualTo("hello_app");
    }

    @Test
    void postgresMatchesLabMinorVersion() {
        // Ví dụ giá trị: "18.7 (Debian 18.7-1.pgdg13+1)". Lệch minor với lab thì đỏ.
        String version = jdbc.sql("show server_version").query(String.class).single();
        assertThat(version).startsWith("18.7");
    }
}