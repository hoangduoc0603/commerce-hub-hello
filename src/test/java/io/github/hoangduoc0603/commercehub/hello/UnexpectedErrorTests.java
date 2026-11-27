package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;
// jwt(): đặt sẵn JwtAuthenticationToken vào SecurityContext, KHÔNG qua JwtDecoder (G0-29). Kiểm token thật: JwtAuthenticationTests.
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Lỗi không lường trước (G0-28 bước 5, rest-api.md mục "Lỗi 500"): body không lộ gì, log có đủ để gỡ lỗi,
// và hai bên nối với nhau bằng traceId.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
// Bắt stdout/stderr trong lúc test để kiểm dòng log (Boot log ra console).
@ExtendWith(OutputCaptureExtension.class)
class UnexpectedErrorTests {

    // Giống message của exception thật khi DB lỗi: có chuỗi kết nối, thứ tuyệt đối không được ra tới client.
    static final String SECRET = "jdbc:postgresql://pg-main-rw/hello?password=s3cr3t";

    @Autowired
    MockMvcTester mvc;

    @Test
    void unexpectedExceptionReturns500ProblemWithoutLeaking(CapturedOutput output) throws Exception {
        MvcTestResult result = mvc.get().uri("/test/boom").with(jwt()).exchange();

        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Internal Server Error");
        // detail cố định: message của exception lạ có thể chứa secret, SQL, PII (RFC 9457 §3.1.4, N11).
        assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Unexpected error");
        assertThat(result).bodyText().doesNotContain("s3cr3t").doesNotContain("IllegalStateException");

        // Từ traceId trong body tìm được dòng log có stack trace: đó là lý do có traceId.
        String traceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(output).contains("ERROR").contains(traceId).contains("IllegalStateException");
    }

    // @TestConfiguration lồng trong class test được nạp thêm vào context; /test/boom chỉ có khi chạy test này.
    // Cùng cách với VirtualThreadTests: class lồng bị loại khỏi component scan nên đăng ký bằng @Bean.
    @TestConfiguration
    static class BoomConfig {
        @Bean
        BoomController boomController() {
            return new BoomController();
        }
    }

    @RestController
    static class BoomController {
        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("connection failed: " + SECRET);
        }
    }
}
