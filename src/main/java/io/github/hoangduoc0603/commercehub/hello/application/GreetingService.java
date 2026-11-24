package io.github.hoangduoc0603.commercehub.hello.application;

import java.util.Optional;
import java.util.UUID;

import io.github.hoangduoc0603.commercehub.hello.application.port.out.GreetingRepository;
import io.github.hoangduoc0603.commercehub.hello.application.port.out.IdGenerator;
import io.github.hoangduoc0603.commercehub.hello.domain.Greeting;
import org.springframework.stereotype.Service;

// Use case của hello. Chỉ phụ thuộc port (interface), không import adapter (arc42 5.2).
// Không @Transactional: mỗi thao tác là một câu SQL, autocommit là đủ. Use case ghi nhiều bảng ở G1 mới cần.
@Service
public class GreetingService {

    private final GreetingRepository greetings;
    private final IdGenerator ids;

    // Một constructor: Spring tự tiêm, không cần @Autowired.
    public GreetingService(GreetingRepository greetings, IdGenerator ids) {
        this.greetings = greetings;
        this.ids = ids;
    }

    public Greeting create(String name) {
        return greetings.insert(ids.next(), name);
    }

    public Optional<Greeting> get(UUID id) {
        return greetings.findById(id);
    }
}