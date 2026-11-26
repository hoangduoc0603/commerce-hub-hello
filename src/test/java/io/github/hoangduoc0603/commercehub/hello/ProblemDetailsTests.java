package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

// Dạng Problem Details (RFC 9457) cho lỗi HTTP chung, G0-28. Chuẩn: docs/standards/rest-api.md.
// Kiểm JsonPath trên đúng tên field: đổi tên field trong code thì test đỏ (ADR-0019).
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProblemDetailsTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void unknownPathReturns404Problem() {
        // Không handler nào khớp thì Spring ném NoResourceFoundException (là một ErrorResponse).
        assertGenericProblem(mvc.get().uri("/v1/khong-co").exchange(), HttpStatus.NOT_FOUND);
    }

    @Test
    void wrongMethodReturns405ProblemWithAllow() {
        // /v1/greetings/{id} chỉ có GET. RFC 9110 bắt 405 phải có header Allow liệt kê method được phép.
        MvcTestResult result = mvc.delete().uri("/v1/greetings/{id}", UUID.randomUUID()).exchange();
        assertGenericProblem(result, HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(result).hasHeader("Allow", "GET");
    }

    @Test
    void malformedJsonReturns400Problem() {
        // Jackson không parse được thì HttpMessageNotReadableException; Spring chỉ ghi "Failed to read request",
        // không lộ message của parser.
        MvcTestResult result = mvc.post().uri("/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                .content("not json")
                .exchange();
        assertGenericProblem(result, HttpStatus.BAD_REQUEST);
    }

    @Test
    void malformedIdReturns400ProblemWithoutEchoingValue() {
        MvcTestResult result = mvc.get().uri("/v1/greetings/khong-phai-uuid").exchange();
        assertGenericProblem(result, HttpStatus.BAD_REQUEST);
        // Mặc định Spring ghi "Failed to convert 'id' with value: 'khong-phai-uuid'". Giá trị client gửi không
        // được lặp lại trong detail (N11). Chỉ kiểm detail: instance chứa path, xem rest-api.md.
        assertThat(result).bodyJson().extractingPath("$.detail").asString().doesNotContain("khong-phai-uuid");
    }

    @Test
    void unknownGreetingReturns404Problem() {
        // UUID ngẫu nhiên: gần như chắc chắn không có trong bảng. Route khớp, controller chạy, không thấy dòng nào.
        MvcTestResult result = mvc.get().uri("/v1/greetings/{id}", UUID.randomUUID()).exchange();
        assertGenericProblem(result, HttpStatus.NOT_FOUND);
        // Khác 404 đường dẫn lạ ("No static resource ..."): người đọc log biết route có, chỉ thiếu tài nguyên.
        // Client không được parse detail (RFC 9457 §3.1.4); test kiểm để câu chữ không đổi mà không ai hay.
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Greeting not found");
    }

    // Mỗi case: body vi phạm đúng một constraint, và message mong đợi (tiếng Anh, xem rest-api.md).
    // Case cuối: \\u0000 trong text block giữ nguyên dấu \ để Jackson giải mã thành ký tự NUL.
    static Stream<Arguments> invalidNames() {
        return Stream.of(
                Arguments.of("{}", "must not be null"),
                Arguments.of("""
                        {"name": ""}""", "size must be between 1 and 50"),
                Arguments.of("""
                        {"name": "%s"}""".formatted("a".repeat(51)), "size must be between 1 and 50"),
                Arguments.of("""
                        {"name": "a\\u0000b"}""", "must not contain NUL or unpaired surrogate characters"));
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void invalidNameReturns400WithErrors(String body, String expectedDetail) {
        MvcTestResult result = mvc.post().uri("/v1/greetings")
                .contentType(MediaType.APPLICATION_JSON)
                // Trình duyệt tiếng Đức: message vẫn phải là tiếng Anh (locale khóa ở application.yaml).
                .header("Accept-Language", "de")
                .content(body)
                .exchange();
        assertGenericProblem(result, HttpStatus.BAD_REQUEST);
        // Mỗi body chỉ vi phạm một constraint nên có đúng một phần tử.
        assertThat(result).bodyJson().extractingPath("$.errors.length()").isEqualTo(1);
        // JSON Pointer dạng fragment (RFC 9457 §3, RFC 6901 §6): trỏ tới field "name" ở gốc của body.
        assertThat(result).bodyJson().extractingPath("$.errors[0].pointer").isEqualTo("#/name");
        assertThat(result).bodyJson().extractingPath("$.errors[0].detail").isEqualTo(expectedDetail);
        // Không lặp lại giá trị đã gửi (N11): không có rejectedValue trong body.
        assertThat(result).bodyText().doesNotContain("aaaaaaaaaa");
    }

    // Dạng chung của lỗi HTTP: type about:blank, title là reason phrase, status trùng status HTTP.
    private static void assertGenericProblem(MvcTestResult result, HttpStatus status) {
        assertThat(result)
                .hasStatus(status)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        // Spring 7.0 không ghi type khi là about:blank; RFC 9457 §4.2.1: vắng type nghĩa là about:blank.
        assertThat(result).bodyJson().doesNotHavePath("$.type");
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(status.getReasonPhrase());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
    }
}
