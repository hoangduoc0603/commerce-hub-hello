package io.github.hoangduoc0603.commercehub.hello.domain;

import java.time.Instant;
import java.util.UUID;

// Lời chào đã lưu. Record: bất biến, equals/hashCode theo mọi field. Không import Spring (arc42 5.2).
// Instant cho thời điểm: luôn là UTC, không mang múi giờ (arc42 mục 8.6).
public record Greeting(UUID id, String name, Instant createdAt) {
}