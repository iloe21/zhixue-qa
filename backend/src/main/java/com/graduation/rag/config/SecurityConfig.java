package com.graduation.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graduation.rag.common.Result;
import com.graduation.rag.security.JwtAuthenticationFilter;
import com.graduation.rag.security.JwtUtil;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Spring Security 配置：JWT 无状态认证
 *
 * 参考项目踩坑经验：
 * 1. permitAll 只放行精确路径，禁止 /** 通配符（会绕过方法级鉴权）
 * 2. 自定义 401 EntryPoint（JSON 响应）；否则未认证请求默认返回 403，前端无法区分
 * 3. 403 AccessDeniedHandler 同样返回 JSON
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ASYNC/ERROR 派发必须放行：SSE 等异步请求完成后 Tomcat 会再次派发过过滤器链，
                // 此时线程上已无 SecurityContext，不放行会中断已提交的响应，
                // 导致客户端收到 "terminated"（Spring Security 6 + SSE 实测踩坑）
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                // 公开接口：精确路径 + 方法限定（禁止 /** 通配，防绕过方法级鉴权）
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                        "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/health", "/api/auth/captcha").permitAll()
                // 其余接口一律需要认证（后续模块用 @PreAuthorize 做角色细分）
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) ->
                        writeError(res, 401, "未登录或登录已过期"))
                .accessDeniedHandler((req, res, ex) ->
                        writeError(res, 403, "没有权限执行此操作")))
            .addFilterBefore(new JwtAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeError(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(Result.error(status, message)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
