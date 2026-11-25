package io.github.hoangduoc0603.commercehub.hello.adapter.in.web;

import io.github.hoangduoc0603.commercehub.hello.application.GreetingService;
import io.github.hoangduoc0603.commercehub.hello.domain.Greeting;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

/**
 * @author duochv on 11/25/26 01:52
 */

@RestController
@RequestMapping("/v1/greetings")
public class GreetingController {
    private final GreetingService greetings;

    public GreetingController(GreetingService greetings) {
        this.greetings = greetings;
    }

    // @Valid: Spring kiểm ràng buộc của CreateGreetingRequest trước khi gọi method; sai thì 400, không chạm DB.
    @PostMapping
    ResponseEntity<GreetingResponse> create(@Valid @RequestBody CreateGreetingRequest request) {
        Greeting greeting = greetings.create(request.name());
        URI location = UriComponentsBuilder.fromPath("/v1/greetings/{id}")
                .buildAndExpand(greeting.id())
                .toUri();
        return ResponseEntity.created(location)
                .body(GreetingResponse.from(greeting));
    }

    @GetMapping("/{id}")
    ResponseEntity<GreetingResponse> get(@PathVariable UUID id) {
        // ResponseEntity.of: có giá trị thì 200, Optional rỗng thì 404 (thân rỗng; Problem Details ở ticket 28).
        return ResponseEntity.of(greetings.get(id).map(GreetingResponse::from));
    }

    // DTO là record lồng trong controller: chỉ lớp web dùng. Tên field là tên trong JSON, đổi là đổi API.
    // Cùng luật với CHECK (char_length(name) BETWEEN 1 AND 50) của bảng greeting. Ràng buộc trong DB vẫn giữ làm lớp
    // chặn cuối. @NotNull riêng vì @Size coi null là hợp lệ. Không @NotBlank: spec ghi "1–50 ký tự", DB nhận "   ".
    // @Size đếm đơn vị UTF-16 nên với emoji thì chặt hơn char_length; lệch theo hướng an toàn.
    // @Pattern chặn hai thứ JSON hợp lệ mà DB không lưu đúng được: ký tự NUL (PostgreSQL text từ chối 0x00, thiếu chặn
    // thì thành 500) và surrogate lẻ (UTF-8 không mã hóa được, driver lưu thành "?" mà không báo lỗi). \p{Cs} chỉ khớp
    // surrogate lẻ: regex Java coi một cặp surrogate (emoji) là một code point, nên emoji vẫn được nhận.
    record CreateGreetingRequest(
            @NotNull @Size(min = 1, max = 50) @Pattern(regexp = "[^\\x00\\p{Cs}]*") String name) {
    }

    record GreetingResponse(UUID id, String name, Instant createdAt) {
        static GreetingResponse from(Greeting greeting) {
            return new GreetingResponse(greeting.id(), greeting.name(), greeting.createdAt());
        }
    }
}
