package io.github.hoangduoc0603.commercehub.hello.adapter.out.persistence;

import io.github.hoangduoc0603.commercehub.hello.application.port.out.GreetingRepository;
import io.github.hoangduoc0603.commercehub.hello.domain.Greeting;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * @author duochv on 11/25/26 01:24
 */

@Repository
public class JdbcGreetingRepository implements GreetingRepository {
    private final JdbcClient jdbc;

    public JdbcGreetingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Greeting insert(UUID id, String name) {
        OffsetDateTime createdAt = jdbc.sql("""
                insert into greeting (id, name) values (:id, :name)
                returning created_at""")
                .param("id", id)
                .param("name", name)
                .query(OffsetDateTime.class)
                .single();

        return new Greeting(id, name, createdAt.toInstant());
    }

    @Override
    public Optional<Greeting> findById(UUID id) {
        // Liệt kê cột, không SELECT *: thêm cột ở migration sau không đổi kết quả ở đây.
        return jdbc.sql("select id, name, created_at from greeting where id = :id")
                .param("id", id)
                .query((rs, rowNum) -> new Greeting(
                        rs.getObject("id", UUID.class),
                        rs.getString("name"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant()))
                .optional();
    }
}
