package com.graduation.rag.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graduation.rag.entity.User;
import com.graduation.rag.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 启动时自动初始化管理员账户（admin / admin123）
 *
 * 为什么不在 init.sql 里插 INSERT？
 * —— BCrypt 哈希必须由程序生成；手写/复制的假哈希会导致"密码正确却无法登录"
 *    （参考项目踩过的坑）。此方式保证哈希与密码永远匹配。
 */
@Component
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(new QueryWrapper<User>().eq("username", "admin"));
        if (count == null || count == 0) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("系统管理员");
            admin.setRole("admin");
            admin.setStatus(1);
            userMapper.insert(admin);
            log.info("已初始化管理员账户 admin / admin123（请首次登录后及时修改密码）");
        }
    }
}
