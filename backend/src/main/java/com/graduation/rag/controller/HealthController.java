package com.graduation.rag.controller;

import com.graduation.rag.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查接口（工程骨架验收用）：
 * 验证 Web 层、数据库连接是否正常
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("app", "rag-learning-backend");
        info.put("time", LocalDateTime.now().toString());
        try {
            String db = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
            Integer tables = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()",
                    Integer.class);
            info.put("database", db);
            info.put("tables", tables);
        } catch (Exception e) {
            info.put("database", "连接失败: " + e.getMessage());
        }
        return Result.ok(info);
    }
}
