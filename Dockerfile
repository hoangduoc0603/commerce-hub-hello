# ---------- Stage 1: tách fat jar thành 4 layer ----------
# --platform=$BUILDPLATFORM: stage này chạy trên kiến trúc của máy build (arm64 trên Mac, amd64 trên CI).
# Tách jar không phụ thuộc kiến trúc, nên khi CI build image amd64 (bước 9) stage này không phải
# chạy dưới giả lập. Trên Mac build native thì $BUILDPLATFORM trùng nền đích, cờ này không đổi gì.
FROM --platform=$BUILDPLATFORM eclipse-temurin:25-jre-noble@sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c AS extract
WORKDIR /builder
# Jar do Gradle build TRƯỚC docker build (container-image.md): Dockerfile không biên dịch.
COPY build/libs/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Stage 2: runtime ----------
# JRE (không phải JDK) trên Ubuntu 24.04, Docker Official Image, ghim digest của image index
# (đa kiến trúc). Dependabot ecosystem docker mở PR khi digest đổi.
FROM eclipse-temurin:25-jre-noble@sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c

# User hệ thống với UID/GID số cố định. UID số để kubelet kiểm được runAsNonRoot (tên user thì không);
# không home, không shell đăng nhập.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin app

WORKDIR /application
# 4 layer theo thứ tự ít đổi → hay đổi. Sửa code chỉ đổi layer cuối (application, ~12 kB).
# File thuộc root, user 10001 chỉ đọc: app không sửa được chính nó.
COPY --from=extract /builder/extracted/dependencies/ ./
COPY --from=extract /builder/extracted/spring-boot-loader/ ./
COPY --from=extract /builder/extracted/snapshot-dependencies/ ./
COPY --from=extract /builder/extracted/application/ ./

# AOT cache (JEP 514/515): chạy app một lần lúc build, ghi class đã nạp và liên kết vào app.aot.
# Chạy trong RUNTIME stage: cache chỉ dùng được với đúng jar, JDK, OS và kiến trúc lúc train.
# spring.context.exit=onRefresh: app tự thoát sau khi context khởi tạo xong.
# Training run không được nối DB (container-image.md, mục "AOT cache"; Spring Boot how-to "AOT Cache": cấu hình riêng
# cho training run để tránh tương tác với dịch vụ ngoài). Từ G0-26, bean JdbcClient cần DataSource có URL, thiếu thì
# context lỗi "Failed to determine a suitable driver class". URL giả chỉ cho lệnh này: Hikari chỉ mở kết nối ở lần
# getConnection() đầu, mà refresh không gọi lần nào (Flyway tắt mặc định). Host .invalid (RFC 6761) không bao giờ
# phân giải được, nên nếu sau này có bean nối DB lúc khởi động, build đỏ ngay thay vì train nhầm vào một DB thật.
# Đã đo 2026-11-25: image chạy -XX:AOTMode=on ở profile migrate và runtime, khởi động 0,6 s (không cache 1,3 s).
# Chạy trước USER: còn là root nên ghi được vào /application; app.aot thuộc root, user 10001 chỉ đọc.
# Gỡ mọi biến OTEL_* trước khi train (G0-28): trên runner CI, BuildKit gắn biến OTEL_* trỏ tới socket trace của nó
# (unix:///dev/otel-grpc.sock) vào bước RUN; Boot 4 đọc biến OTEL_* thành property, nên tạo exporter gRPC và lỗi
# "Invalid endpoint", build đỏ. Tái hiện trên máy 2026-11-26: OTEL_TRACES_EXPORTER=otlp cộng endpoint đó và
# protocol grpc thì app không khởi động, kể cả khi có management.tracing.export.otlp.enabled=false ở yaml hay -D.
# issuer-uri, jwk-set-uri giả (G0-29): thiếu thì không có bean JwtDecoder và training run dừng lúc khởi động (CI đỏ ở
# PR hello#11). .invalid như datasource; JWKS tải lười, không request nào gọi tới. Runtime lấy giá trị thật từ env.
RUN for v in $(env | grep -o '^OTEL_[A-Za-z0-9_]*'); do unset "$v"; done; \
    java -XX:AOTCacheOutput=app.aot -Dspring.context.exit=onRefresh \
        -Dspring.datasource.url=jdbc:postgresql://aot-training.invalid:5432/hello \
        -Dspring.security.oauth2.resourceserver.jwt.issuer-uri=https://aot-training.invalid/realms/commerce-hub \
        -Dspring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://aot-training.invalid/realms/commerce-hub/protocol/openid-connect/certs \
        -jar application.jar

# Nhãn tĩnh (G0-23 Q8). revision, version, created do CI đặt lúc build (labels: của build-push-action trong
# platform/.github/workflows/container-image.yml), vì chỉ CI biết giá trị. Image build ở máy thiếu revision
# và created, còn version thừa hưởng "24.04" từ nhãn của base Ubuntu (đo 2026-11-17); CI đặt đè.
# LABEL chỉ là metadata, không tạo layer. source cũng do CI đặt; để đây cho image build ở máy.
# base.digest phải trùng digest ở FROM. Khi Dependabot đổi FROM, sửa dòng này trong cùng PR
# (Dependabot có tự sửa label không: chưa xác minh, kiểm ở PR digest đầu tiên).
LABEL org.opencontainers.image.source="https://github.com/hoangduoc0603/commerce-hub-hello" \
      org.opencontainers.image.title="hello" \
      org.opencontainers.image.base.name="docker.io/library/eclipse-temurin:25-jre-noble" \
      org.opencontainers.image.base.digest="sha256:e3b6f7887a50f20d8f07abf279d8a81a5953fb398a9555bd9edd56b9bd15968c"

USER 10001:10001
# Chỉ để ghi lại: 8080 cổng chính, 8081 Actuator (bước 4). Không mở cổng nào thật.
EXPOSE 8080 8081
# Dạng exec (mảng JSON): java là PID 1, nhận SIGTERM, chạy graceful shutdown.
# -XX:AOTCache: nạp cache. Mặc định AOTMode=auto: cache hỏng hay lệch thì chạy tiếp không cache,
# nên kiểm bằng -Xlog:aot và -XX:AOTMode=on.
ENTRYPOINT ["java", "-XX:AOTCache=app.aot", "-jar", "application.jar"]