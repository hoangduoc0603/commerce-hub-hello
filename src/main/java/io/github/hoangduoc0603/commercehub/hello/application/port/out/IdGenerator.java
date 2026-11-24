package io.github.hoangduoc0603.commercehub.hello.application.port.out;

import java.util.UUID;

/**
 * @author duochv on 11/25/26 00:58
 */
public interface IdGenerator {
    // Mỗi lần gọi trả một ID mới, lớn hơn ID trước theo thứ tự byte (cùng JVM).
    UUID next();
}
