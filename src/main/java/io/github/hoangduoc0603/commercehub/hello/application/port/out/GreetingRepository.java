package io.github.hoangduoc0603.commercehub.hello.application.port.out;

import java.util.Optional;
import java.util.UUID;

import io.github.hoangduoc0603.commercehub.hello.domain.Greeting;

// Port lưu trữ: use case chỉ biết interface này, không biết JdbcClient hay SQL.
public interface GreetingRepository {

    // Lưu lời chào mới; created_at do DB đặt (DEFAULT now()), nên trả về Greeting đầy đủ.
    Greeting insert(UUID id, String name);

    // Optional.empty() khi không có: controller đổi thành 404.
    Optional<Greeting> findById(UUID id);
}