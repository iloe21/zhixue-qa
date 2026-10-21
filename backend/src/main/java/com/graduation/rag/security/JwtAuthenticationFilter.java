package com.graduation.rag.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：解析 Authorization: Bearer xxx，成功则写入 SecurityContext
 *
 * 注意：不加 @Component（避免被 Servlet 容器与安全链重复注册），
 * 由 SecurityConfig 手动 new 后加入安全过滤器链
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims claims = jwtUtil.parse(header.substring(7));
                Long userId = claims.get("userId", Number.class).longValue();
                String username = claims.getSubject();
                String role = claims.get("role", String.class);

                LoginUser principal = new LoginUser(userId, username, role);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        principal, null,
                        // 权限保持与数据库角色值同大小写（ROLE_admin）：
                        // hasRole('admin') 拼接 ROLE_ 前缀后做大小写敏感比较，
                        // 若此处 toUpperCase 会导致管理员也被 403（实测踩坑）
                        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception ignored) {
                // token 无效/过期/被篡改：保持未认证状态，由 EntryPoint 统一返回 401 JSON
            }
        }
        chain.doFilter(request, response);
    }
}
