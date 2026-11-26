package io.github.hoangduoc0603.commercehub.hello.adapter.in.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    // @Valid @RequestBody sai thì Spring ném MethodArgumentNotValidException. Mặc định body chỉ có
    // "Invalid request content."; thêm extension errors[] để client biết field nào sai (rest-api.md).
    // G1 có constraint trên @PathVariable/@RequestParam thì thêm handleHandlerMethodValidationException.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        // ProblemDetail sẵn có của exception (status 400, detail "Invalid request content."); chỉ thêm errors.
        ProblemDetail body = ex.getBody();
        List<FieldViolation> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldViolation(
                        // ObjectError không phải FieldError (constraint ở cấp class) trỏ về gốc của body: "#".
                        error instanceof FieldError fieldError ? toJsonPointer(fieldError.getField()) : "#",
                        // Chỉ message, không bao giờ getRejectedValue(): giá trị có thể là PII (N11).
                        error.getDefaultMessage()))
                // Bean Validation trả vi phạm theo thứ tự không cố định (Set). Sắp xếp để cùng request luôn ra
                // cùng body; detail có thể null theo API nên nullsFirst.
                .sorted(Comparator.comparing(FieldViolation::pointer)
                        .thenComparing(FieldViolation::detail, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        // setProperty: ProblemDetailJacksonMixin đưa map properties lên cấp đầu của JSON (đã đo, rest-api.md).
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    // "items[0].qty" thành "#/items/0/qty" (RFC 6901). Đúng khi tên field record DTO trùng tên JSON: không đổi tên
    // bằng @JsonProperty (rest-api.md). Tên field Java không có ký tự phải percent-encode trong fragment URI.
    // Package-private static để JsonPointerTests gọi thẳng.
    static String toJsonPointer(String field) {
        StringBuilder pointer = new StringBuilder("#");
        for (String token : field.replace("[", ".").replace("]", "").split("\\.")) {
            // Thoát theo RFC 6901 §3: "~" trước rồi mới "/", không thì "~1" vừa sinh ra bị thoát lần nữa.
            pointer.append('/').append(token.replace("~", "~0").replace("/", "~1"));
        }
        return pointer.toString();
    }

    // Một phần tử của errors[]. Record: Jackson ghi field theo thứ tự khai báo, tên field là tên trong JSON.
    record FieldViolation(String pointer, String detail) {
    }
}
