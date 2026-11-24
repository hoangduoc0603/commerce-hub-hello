package io.github.hoangduoc0603.commercehub.hello.adapter.out.id;

import java.util.UUID;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import io.github.hoangduoc0603.commercehub.hello.application.port.out.IdGenerator;
import org.springframework.stereotype.Component;

// UUIDv7 theo RFC 9562 bằng java-uuid-generator. Bean singleton: một generator cho cả app, nên thứ tự tăng dần giữ
// được giữa các luồng (generator đồng bộ bên trong).
@Component
public class UuidV7Generator implements IdGenerator {

    // timeBasedEpochGenerator: cùng millisecond thì cộng 1 vào phần ngẫu nhiên, nên tăng ngặt.
    // Giá phải trả (javadoc JUG): ID trong cùng ms gần nhau, đoán được ID kế tiếp. Chấp nhận vì ID không phải bí mật:
    // quyền truy cập do JWT (ticket 29) và kiểm Workspace (G1) quyết định, không dựa vào việc ID khó đoán.
    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    @Override
    public UUID next() {
        return generator.generate();
    }
}