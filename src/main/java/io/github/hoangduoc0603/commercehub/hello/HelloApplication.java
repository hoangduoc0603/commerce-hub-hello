package io.github.hoangduoc0603.commercehub.hello;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HelloApplication {

    // Tạm: đổi bytecode để đo build ấm trên CI (G0-23 bước 10); commit sau revert.
    static final String WARM_BUILD_PROBE = "g0-23-warm-1";

    public static void main(String[] args) {
        SpringApplication.run(HelloApplication.class, args);
    }

}
