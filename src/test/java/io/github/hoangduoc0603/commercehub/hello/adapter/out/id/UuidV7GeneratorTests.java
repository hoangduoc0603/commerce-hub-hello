package io.github.hoangduoc0603.commercehub.hello.adapter.out.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

// Unit test thuần: không Spring, không Docker, chạy trong mili giây.
class UuidV7GeneratorTests {

    private final UuidV7Generator ids = new UuidV7Generator();

    @Test
    void isVersion7WithRfc9562Variant() {
        UUID id = ids.next();
        assertThat(id.version()).isEqualTo(7);
        // variant() trả 2 cho bit "10": variant của RFC 9562 (cũng là RFC 4122 cũ).
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void embedsCurrentUnixMillis() {
        long before = System.currentTimeMillis();
        UUID id = ids.next();
        long after = System.currentTimeMillis();
        // 48 bit đầu là Unix epoch millis: dịch 64 bit cao sang phải 16 bit (bỏ version và 12 bit sau nó).
        assertThat(id.getMostSignificantBits() >>> 16).isBetween(before, after);
    }

    @Test
    void idsAreStrictlyIncreasingInDatabaseOrder() {
        // 1 000 ID sinh liên tiếp, phần lớn rơi vào cùng một millisecond: kiểm luôn phần tăng dần trong cùng ms.
        // So theo chuỗi hex: thứ tự chữ của chuỗi trùng thứ tự byte, cũng là thứ tự PostgreSQL sắp uuid.
        // Không dùng UUID.compareTo: nó so hai long có dấu, không phải thứ tự byte.
        List<String> generated = Stream.generate(ids::next).limit(1_000).map(UUID::toString).toList();
        // Sắp tăng (không giảm) và không trùng, nên là tăng ngặt.
        assertThat(generated).isSortedAccordingTo(Comparator.naturalOrder()).doesNotHaveDuplicates();
    }
}