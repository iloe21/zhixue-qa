package com.graduation.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graduation.rag.dto.ChangePasswordRequest;
import com.graduation.rag.dto.LoginRequest;
import com.graduation.rag.dto.LoginResponse;
import com.graduation.rag.dto.RegisterRequest;
import com.graduation.rag.dto.UpdateProfileRequest;
import com.graduation.rag.entity.User;
import com.graduation.rag.exception.BusinessException;
import com.graduation.rag.mapper.UserMapper;
import com.graduation.rag.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务：注册 / 登录（含图形验证码校验）/ 个人信息 / 修改密码
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil, CaptchaService captchaService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.captchaService = captchaService;
    }

    /**
     * 注册：用户名唯一校验 → BCrypt 加密 → 默认 student 角色
     */
    public void register(RegisterRequest req) {
        Long count = userMapper.selectCount(
                new QueryWrapper<User>().eq("username", req.getUsername()));
        if (count != null && count > 0) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() != null && !req.getNickname().isBlank()
                ? req.getNickname() : req.getUsername());
        user.setEmail(req.getEmail());
        user.setRole("student");
        user.setStatus(1);
        userMapper.insert(user);
    }

    /**
     * 登录：先校验图形验证码（一次性），再校验密码与账户状态 → 颁发 JWT
     * 注意：用户不存在与密码错误返回同一提示，避免暴露用户名是否存在
     */
    public LoginResponse login(LoginRequest req) {
        // 1. 图形验证码校验（无论后续成败，该验证码已销毁，防暴力破解）
        if (!captchaService.verify(req.getCaptchaId(), req.getCaptchaCode())) {
            throw new BusinessException("验证码错误或已过期");
        }
        // 2. 账户密码校验
        User user = userMapper.selectOne(
                new QueryWrapper<User>().eq("username", req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() == 0) {
            throw new BusinessException(403, "账号已被禁用，请联系管理员");
        }
        return new LoginResponse(jwtUtil.generate(user), user);
    }

    public User getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /**
     * 修改个人信息：白名单字段更新（nickname/email），
     * role/status 等敏感字段绝不接受前端传入（防 Mass Assignment）
     */
    public User updateProfile(Long userId, UpdateProfileRequest req) {
        User user = getById(userId);
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname());
        }
        if (req.getEmail() != null) {
            user.setEmail(req.getEmail());
        }
        userMapper.updateById(user);
        return user;
    }

    /**
     * 修改密码：先验证原密码
     */
    public void changePassword(Long userId, ChangePasswordRequest req) {
        User user = getById(userId);
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(user);
    }
}
