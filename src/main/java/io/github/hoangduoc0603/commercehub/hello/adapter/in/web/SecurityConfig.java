package io.github.hoangduoc0603.commercehub.hello.adapter.in.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.web.WebServerNamespace;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * @author duochv on 11/27/26 10:52
 */
// G0-29: mọi route bắt JWT (docs/standards/keycloak.md mục "Resource server", repo docs). Có bean SecurityFilterChain
// này thì chain mặc định của Boot (OAuth2ResourceServerWebSecurityAutoConfiguration, ManagementWebSecurity...) tự lùi.
// Chỉ tạo khi có web server servlet: profile migrate (web-application-type: none, Job PreSync G0-27) không có bean
// HttpSecurity; thiếu điều kiện này thì Job lỗi lúc khởi động và Argo CD dừng sync (MigrateProfileTests bắt được).
// Không dùng @Profile("!migrate"): lý do thật là "không web thì không filter chain", không phải tên profile.
@ConditionalOnWebApplication(type = Type.SERVLET)
// proxyBeanMethods = false: không @Bean nào gọi @Bean khác, khỏi tạo proxy CGLIB.
// Package-private như ApiExceptionHandler: Spring vẫn quét được, code khác không cần gọi tới.
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            // Qualifier bắt buộc: Boot có thêm bean DefaultErrorAttributes cũng là HandlerExceptionResolver.
            // "handlerExceptionResolver" là chuỗi resolver của MVC, trong đó ExceptionHandlerExceptionResolver gọi
            // các @ExceptionHandler của @RestControllerAdvice.
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        // Lỗi trong filter không tới @RestControllerAdvice (rest-api.md): chuyển cho resolver của MVC để 401 đi cùng
        // đường với mọi lỗi khác và có traceId (Q2 ticket G0-29). handler = null: chưa chọn controller nào.
        AuthenticationEntryPoint entryPoint = (request, response, ex) -> {
            // null = không resolver nào nhận, response chưa được ghi gì. Không có nhánh này thì client nhận 200 body rỗng.
            if (resolver.resolveException(request, response, null, ex) == null) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            }
        };
        http
                // Luật xét từ trên xuống, khớp luật nào thì dừng. anyRequest() phải đứng cuối.
                .authorizeHttpRequests(requests -> requests
                        // /livez, /readyz ở cổng chính (management.endpoint.health.probes.add-additional-paths).
                        // Kubelet gọi không có token; chặn thì pod không bao giờ Ready.
                        // toAnyEndpoint() KHÔNG khớp hai đường này: nó chỉ lấy đường gốc /actuator/** của endpoint.
                        .requestMatchers(EndpointRequest.toAdditionalPaths(WebServerNamespace.SERVER,
                                HealthEndpoint.class)).permitAll()
                        // Mọi endpoint Actuator ở cổng quản trị 8081: Prometheus (G0-30) scrape không có token.
                        // 8081 không có HTTPRoute nên không ra ngoài cụm; ở cổng chính matcher này không khớp gì
                        // (management.server.port khác cổng chính), nên không mở /actuator trên 8080.
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        // Còn lại: phải có JWT hợp lệ. Phân quyền theo Member/Workspace là việc của G1.
                        .anyRequest().authenticated())
                // Bật BearerTokenAuthenticationFilter + JwtDecoder do Boot dựng từ spring.security.oauth2.resourceserver.*.
                // withDefaults(): không tự đổi claim thành quyền (keycloak.md: realm role không phải quyền Workspace).
                // authenticationEntryPoint dùng cho cả token hỏng (BearerTokenAuthenticationFilter) lẫn thiếu token
                // (ExceptionTranslationFilter). Chain chỉ có một entry point nên nó áp cho mọi request, kể cả
                // trình duyệt gửi Accept: text/html.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(entryPoint)
                        // Lỗi KHÔNG kiểm được token (tải JWKS hỏng: DNS, TLS, Keycloak sập) cũng đi qua entry point,
                        // thay vì bị ném lại ra khỏi chain. Ném lại thì Tomcat forward tới /error, lượt ERROR dispatch
                        // không có authentication nên ra 401 "Bearer token required": client tưởng lỗi của mình (bước 6
                        // ticket G0-29, đo trên lab). Ở đây ApiExceptionHandler trả 503 có traceId.
                        // DSL 7.1.2 không có chỗ đặt failure handler: dùng ObjectPostProcessor, chạy ngay sau khi
                        // configurer tạo filter. Chỉ áp cho BearerTokenAuthenticationFilter (Spring lọc theo kiểu generic).
                        // Phụ thuộc chi tiết bên trong configurer: nâng Spring Security thì JwksUnavailableTests canh.
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                AuthenticationEntryPointFailureHandler failureHandler =
                                        new AuthenticationEntryPointFailureHandler(entryPoint);
                                // Mặc định true: AuthenticationServiceException bị ném lại. false: tới entry point.
                                failureHandler.setRethrowAuthenticationServiceException(false);
                                filter.setAuthenticationFailureHandler(failureHandler);
                                return filter;
                            }
                        }))
                // Mỗi request tự mang token: không tạo HttpSession (không cookie JSESSIONID, không tốn RAM cho session).
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF lợi dụng việc trình duyệt tự gửi cookie. Bearer token không được trình duyệt tự gửi, và trình duyệt
                // không gọi hello trực tiếp (ADR-0011: BFF giữ phiên). Giữ CSRF thì mọi POST không có token ra 403
                // thay vì 401, sai ngữ nghĩa. Spring Security docs: tắt khi chỉ phục vụ client không phải trình duyệt.
                .csrf(AbstractHttpConfigurer::disable);
        // Boot 4.1 tự khai method này không có "throws Exception"; giữ như vậy.
        return http.build();
    }
}
