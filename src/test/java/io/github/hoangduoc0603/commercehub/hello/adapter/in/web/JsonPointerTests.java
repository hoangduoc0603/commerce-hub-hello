package io.github.hoangduoc0603.commercehub.hello.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

// Đổi đường dẫn field của BindingResult sang JSON Pointer (RFC 6901). Không cần Spring.
class JsonPointerTests {

    @ParameterizedTest
    @CsvSource({
            "name, #/name",
            "address.city, #/address/city",
            "items[0].qty, #/items/0/qty",
            // RFC 6901 §3: "~" viết thành "~0", "/" viết thành "~1".
            "a~b, #/a~0b",
            "a/b, #/a~1b"
    })
    void fieldPathBecomesJsonPointer(String field, String pointer) {
        assertThat(ApiExceptionHandler.toJsonPointer(field)).isEqualTo(pointer);
    }
}
