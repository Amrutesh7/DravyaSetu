package com.dravya.setu.config;

import com.dravya.setu.security.JwtAuthenticationFilter;
import com.dravya.setu.common.ErrorResponse;
import com.dravya.setu.common.RequestIdFilter;
import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> allowedOriginPatterns;
    private final String serviceOrigin;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            List<String> allowedOriginPatterns) {
        this(jwtAuthenticationFilter, allowedOriginPatterns, "");
    }

    @Autowired
    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            @Value("${app.cors.allowed-origin-patterns}") List<String> allowedOriginPatterns,
            @Value("${RENDER_EXTERNAL_URL:}") String serviceOrigin) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOriginPatterns = allowedOriginPatterns;
        this.serviceOrigin = serviceOrigin.strip().replaceAll("/+$", "");
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                writeSecurityError(response, 401, "AUTH_REQUIRED",
                                        "Authentication is required", request))
                        .accessDeniedHandler((request, response, exception) ->
                                writeSecurityError(response, 403, "AUTH_FORBIDDEN",
                                        "You do not have permission for this resource", request)))

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/auth/register",
                                "/health",
                                "/languages",
                                "/actuator/health",
                                "/actuator/info",
                                "/auth/login",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/auth/verify-email",
                                "/auth/resend-verification",
                                "/auth/verify-reset-otp",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/webjars/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        // Render terminates TLS before the app. Allow its configured public origin
        // for Swagger requests without trusting caller-supplied forwarding headers.
        if (!serviceOrigin.isEmpty()) configuration.addAllowedOrigin(serviceOrigin);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Retry-After"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeSecurityError(jakarta.servlet.http.HttpServletResponse response,
            int status, String code, String message, jakarta.servlet.http.HttpServletRequest request)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_HEADER);
        new ObjectMapper().writeValue(response.getOutputStream(),
                new ErrorResponse(code, message, requestId == null ? null : requestId.toString()));
    }
}
