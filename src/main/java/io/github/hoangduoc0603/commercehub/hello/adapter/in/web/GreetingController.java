package io.github.hoangduoc0603.commercehub.hello.adapter.in.web;

import io.github.hoangduoc0603.commercehub.hello.application.GreetingService;
import io.github.hoangduoc0603.commercehub.hello.domain.Greeting;
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

    @PostMapping
    ResponseEntity<GreetingResponse> create(@RequestBody CreateGreetingRequest request) {
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
    record CreateGreetingRequest(String name) {
    }

    record GreetingResponse(UUID id, String name, Instant createdAt) {
        static GreetingResponse from(Greeting greeting) {
            return new GreetingResponse(greeting.id(), greeting.name(), greeting.createdAt());
        }
    }
}
