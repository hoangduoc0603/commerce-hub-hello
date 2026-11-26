package io.github.hoangduoc0603.commercehub.hello.adapter.in.web;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Một chỗ duy nhất biến exception thành Problem Details (RFC 9457). Chuẩn: docs/standards/rest-api.md (repo docs).
// Kế thừa ResponseEntityExceptionHandler: lớp này đã có @ExceptionHandler cho mọi exception của Spring MVC
// (404 path lạ, 405, 415, 400 JSON hỏng...) và mọi ErrorResponseException; ta chỉ override chỗ cần đổi.
// Không bật spring.mvc.problemdetails.enabled: handler của Boot tự lùi khi đã có bean này.
// Package-private: Spring vẫn quét được, code ngoài adapter.in.web không cần gọi tới.
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    // Mặc định detail là "Failed to convert 'id' with value: '<giá trị>'": lặp lại thứ client gửi. Ở G1 giá trị đó
    // có thể là PII (N11), và body lỗi có thể bị log ở BFF. Chỉ nêu tên tham số và kiểu mong đợi.
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        // getRequiredType() có thể null theo API, dù với @PathVariable thì luôn có.
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "value";
        String detail = "Parameter '%s' must be a valid %s".formatted(ex.getPropertyName(), expected);
        // createProblemDetail: dựng ProblemDetail (title từ status) và cho MessageSource ghi đè detail nếu có
        // message code. Hai null = dùng message code mặc định, không tham số.
        ProblemDetail body = createProblemDetail(ex, status, detail, null, null, request);
        // Đi tiếp qua handleExceptionInternal như mọi handler gốc, để bước 4 gắn traceId ở một chỗ.
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}