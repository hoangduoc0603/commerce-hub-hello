package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.GenericContainer;

// Kiểm hành vi runtime khi đi qua PgBouncer transaction, giống Pooler pg-main-pooler-rw trên lab.
// Cùng cấu hình context với DatabaseConnectionTests nên dùng chung container.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PgBouncerTests {

    @Autowired
    JdbcClient jdbc;

    // Có hai bean kiểu GenericContainer (PostgreSQLContainer cũng là GenericContainer), nên chỉ rõ tên bean.
    @Autowired
    @Qualifier("pgbouncer")
    GenericContainer<?> pgbouncer;

    @Test
    void runtimeConnectionsGoThroughPgBouncer() {
        // inet_client_addr() là IP của bên đang nối vào PostgreSQL. Đi qua PgBouncer thì đó là IP của container
        // PgBouncer trong Docker network. Nối thẳng từ máy thì đó là IP gateway của Docker. Hai giá trị khác nhau,
        // nên test phân biệt được hai đường đi.
        String clientAddr = jdbc.sql("select host(inet_client_addr())").query(String.class).single();
        // PgBouncer chỉ nằm trong một network (bean network), nên lấy phần tử đầu tiên.
        String pgbouncerIp = pgbouncer.getContainerInfo().getNetworkSettings().getNetworks()
                .values().iterator().next().getIpAddress();
        assertThat(clientAddr).isEqualTo(pgbouncerIp);
    }

    @Test
    void preparedStatementsSurviveTransactionPooling() throws Exception {
        // 10 luồng chạy song song, nhiều hơn 8 kết nối thật của pool, để transaction của cùng một kết nối Hikari
        // rơi vào nhiều kết nối thật khác nhau. pg_sleep giữ mỗi transaction đủ lâu cho các luồng chồng lên nhau.
        // 200 lần chạy cùng một câu SQL: mỗi kết nối vượt prepareThreshold=5 của pgjdbc, nên driver chuyển sang
        // prepared statement có tên (S_1...).
        // Nếu PgBouncer không theo dõi prepared statement (max_prepared_statements = 0) thì f.get() ném
        // "prepared statement S_1 already exists" hoặc "does not exist". Đã thử: đỏ 3/3 lần.
        try (var pool = Executors.newFixedThreadPool(10)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                int n = i;
                calls.add(() -> jdbc.sql("select :n + 0 from pg_sleep(0.001)")
                        .param("n", n)
                        .query(Integer.class)
                        .single());
            }
            // invokeAll chờ mọi việc xong; f.get() ném lại exception nếu có việc nào lỗi.
            for (var f : pool.invokeAll(calls)) {
                f.get();
            }
        }
    }
}
