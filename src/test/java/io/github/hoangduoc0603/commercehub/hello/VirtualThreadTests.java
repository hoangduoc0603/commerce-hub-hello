package io.github.hoangduoc0603.commercehub.hello;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

// RANDOM_PORT: khởi động Tomcat thật trên một cổng ngẫu nhiên. Bắt buộc ở đây, vì môi trường
// MOCK mặc định (MockMvc) xử lý request ngay trên thread của JUnit, không qua executor của Tomcat.
// Với MOCK, test này không bao giờ đo được thứ cần đo.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestJwtConfiguration.class})
class VirtualThreadTests {

    @LocalServerPort   // cổng Tomcat thật đang nghe, Spring tiêm vào sau khi server lên
    int port;

    @Autowired
    TestJwts jwts;

    @Test
    void requestIsHandledOnVirtualThread() {
        // RestClient có sẵn trong spring-web (đi cùng starter webmvc): không thêm dependency nào.
        String thread = RestClient.create("http://localhost:" + port)
                .get().uri("/test/thread")
                // G0-29: mọi route bắt JWT, kể cả controller chỉ có trong test.
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwts.valid())
                .retrieve()
                .body(String.class);

        // Thread.toString() của virtual thread có dạng
        // "VirtualThread[#54,tomcat-handler-0]/runnable@ForkJoinPool-1-worker-1".
        // Platform thread có dạng "Thread[#44,http-nio-auto-1-exec-1,5,main]".
        // So chuỗi thay vì trả isVirtual(): khi đỏ, thông báo lỗi cho thấy luôn đang chạy trên thread nào.
        assertThat(thread).startsWith("VirtualThread");
    }

    // @TestConfiguration lồng trong class test được nạp THÊM vào context của app, không thay thế nó.
    // Endpoint /test/thread vì vậy chỉ có khi chạy test này, không lọt vào app thật.
    @TestConfiguration
    static class ThreadProbeConfig {
        @Bean
        ThreadProbeController threadProbeController() {
            return new ThreadProbeController();
        }
    }

    // Controller chỉ dùng để đo: trả về thread đang xử lý request.
    // Class lồng trong class test bị TestTypeExcludeFilter của Spring Boot loại khỏi component scan,
    // nên phải đăng ký bằng @Bean ở trên. Nếu không loại, sẽ có hai bean cùng một mapping và
    // context lỗi "Ambiguous mapping".
    @RestController
    static class ThreadProbeController {
        @GetMapping("/test/thread")
        String thread() {
            return Thread.currentThread().toString();
        }
    }
}