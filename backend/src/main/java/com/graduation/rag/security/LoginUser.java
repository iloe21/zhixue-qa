package com.graduation.rag.security;

/**
 * JWT 解析后放入 SecurityContext 的登录主体
 * Controller 中通过 @AuthenticationPrincipal LoginUser 获取当前用户
 */
public record LoginUser(Long userId, String username, String role) {
}
