package com.graduation.rag.security;

import com.graduation.rag.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JWT 签发/解析测试：锁定 claims 契约与篡改/过期/异密钥三类拒绝行为
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef";

    private User user() {
        User u = new User();
        u.setId(42L);
        u.setUsername("alice");
        u.setRole("student");
        return u;
    }

    @Test
    @DisplayName("签发后解析出 userId/username/role")
    void roundTripClaims() {
        JwtUtil util = new JwtUtil(SECRET, 60_000);
        Claims claims = util.parse(util.generate(user()));
        assertEquals("alice", claims.getSubject());
        assertEquals(42, claims.get("userId", Number.class).intValue());
        assertEquals("student", claims.get("role"));
    }

    @Test
    @DisplayName("篡改 token 拒收")
    void tamperedTokenRejected() {
        JwtUtil util = new JwtUtil(SECRET, 60_000);
        String token = util.generate(user());
        // 翻转 payload 段最后一个字符
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("A") ? "B" : "A") + "x";
        assertThrows(JwtException.class, () -> util.parse(tampered));
    }

    @Test
    @DisplayName("过期 token 拒收")
    void expiredTokenRejected() throws InterruptedException {
        JwtUtil util = new JwtUtil(SECRET, 1);   // 1ms 有效期
        String token = util.generate(user());
        Thread.sleep(20);
        assertThrows(JwtException.class, () -> util.parse(token));
    }

    @Test
    @DisplayName("其他密钥签发的 token 拒收")
    void foreignKeyRejected() {
        String token = new JwtUtil("another-secret-key-0123456789abcdef", 60_000).generate(user());
        assertThrows(JwtException.class, () -> new JwtUtil(SECRET, 60_000).parse(token));
    }
}
