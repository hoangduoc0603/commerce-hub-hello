package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Phần lỗi của tiêu chí nghiệm thu 1 (G0-26): body POST nào bị từ chối, giá trị biên nào được nhận. Chỉ kiểm status.
// Dạng body lỗi (Problem Details, traceId, errors[]) nằm ở ProblemDetailsTests (G0-28).
// Cùng annotation với GreetingApiTests nên dùng chung context và container.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class GreetingApiErrorTests {

    @Autowired
    MockMvcTester mvc;

    // Mỗi phần tử là một body POST phải bị từ chối. "not json": JSON hỏng thì Jackson ném lỗi, Spring trả 400.
    // Hai body cuối là escape JSON (\\u trong text block giữ nguyên dấu \ cho Jackson giải mã):
    // NUL thì PostgreSQL text không lưu được, surrogate lẻ thì UTF-8 không mã hóa được. Thiếu @Pattern thì body có NUL
    // đi xuống DB và thành 500 (đã thấy khi review 2026-11-25).
    static Stream<String> invalidBodies() {
        return Stream.of(
                "{}",
                """
                {"name": null}""",
                """
                {"name": ""}""",
                """
                {"name": "%s"}""".formatted("a".repeat(51)),
                "not json",
                """
                {"name": "a\\u0000b"}""",
                """
                {"name": "a\\ud800b"}""");
    }

    // Emoji là một cặp surrogate hợp lệ (2 đơn vị UTF-16): phải được nhận, để @Pattern không chặn nhầm.
    @Test
    void emojiNameIsAccepted() {
        assertThat(post("""
                {"name": "chào \\ud83d\\udc4b"}""")).hasStatus(HttpStatus.CREATED);
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidCreateRequestReturns400(String body) {
        // Thiếu validation thì 4 case đầu đi xuống DB, chạm NOT NULL/CHECK và thành 500 (đã thấy khi thử).
        assertThat(post(body)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // Giá trị biên được chấp nhận: 1 ký tự, 50 ký tự ASCII, 50 ký tự có dấu ("Đ" là 1 ký tự, 2 byte UTF-8).
    static Stream<String> boundaryNames() {
        return Stream.of("A", "a".repeat(50), "Đ".repeat(50));
    }

    @ParameterizedTest
    @MethodSource("boundaryNames")
    void boundaryNamesAreAccepted(String name) {
        assertThat(post("""
                {"name": "%s"}""".formatted(name))).hasStatus(HttpStatus.CREATED);
    }

    private MvcTestResult post(String body) {
        return mvc.post().uri("/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
