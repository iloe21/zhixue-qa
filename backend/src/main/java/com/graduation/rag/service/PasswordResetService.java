package com.graduation.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graduation.rag.entity.User;
import com.graduation.rag.exception.BusinessException;
import com.graduation.rag.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密码重置服务（邮箱验证码方式，QQ 邮箱 SMTP）
 *
 * 安全设计：
 * 1. 验证码 6 位数字，10 分钟过期，一次性使用
 * 2. 同一用户 60 秒内只能发送一次（防邮件轰炸）
 * 3. 用户名不存在与邮箱不匹配返回同一提示（防枚举）
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private static final long CODE_EXPIRE_MS = 10 * 60 * 1000L;
    private static final long SEND_INTERVAL_MS = 60 * 1000L;

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, ResetEntry> store = new ConcurrentHashMap<>();
    /** 上次发送时间独立记录：验证码被重置消费后，限流依然生效（修复限流绕过） */
    private final Map<String, Long> lastSentAt = new ConcurrentHashMap<>();

    /** 发件人地址（即配置的 QQ 邮箱） */
    @Value("${spring.mail.username}")
    private String fromAddress;

    private record ResetEntry(String code, long expireAt) {
    }

    public PasswordResetService(UserMapper userMapper, PasswordEncoder passwordEncoder, JavaMailSender mailSender) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    /**
     * 发送重置验证码到注册邮箱
     */
    public void sendResetCode(String username, String email) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        // 统一错误提示，防止探测哪些用户名存在/绑定了该邮箱
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()
                || !user.getEmail().trim().equalsIgnoreCase(email.trim())) {
            throw new BusinessException("用户名或邮箱不匹配");
        }

        long now = System.currentTimeMillis();
        Long last = lastSentAt.get(username);
        if (last != null && now - last < SEND_INTERVAL_MS) {
            throw new BusinessException("发送过于频繁，请 1 分钟后再试");
        }

        String code = String.format("%06d", random.nextInt(1000000));
        store.put(username, new ResetEntry(code, now + CODE_EXPIRE_MS));
        lastSentAt.put(username, now);
        log.debug("密码重置验证码 {} -> {}", username, code);   // 仅 DEBUG，生产不输出
        sendMail(user.getEmail(), code);
    }

    /**
     * 校验验证码并重置密码
     */
    public void resetPassword(String username, String code, String newPassword) {
        ResetEntry entry = store.remove(username);   // 一次性使用
        if (entry == null || entry.expireAt() < System.currentTimeMillis() || !entry.code().equals(code)) {
            throw new BusinessException("验证码错误或已过期");
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    private void sendMail(String to, String code) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject("【智学问答】密码重置验证码");
            msg.setText("您正在重置智学问答账号的密码。\n\n"
                    + "验证码：" + code + "（10 分钟内有效）\n\n"
                    + "如非本人操作，请忽略本邮件，不要将验证码告知任何人。");
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("发送密码重置邮件失败: to={}", to, e);
            throw new BusinessException(500, "邮件发送失败，请稍后重试或联系管理员");
        }
    }
}
