package com.graduation.rag.dto;

import com.graduation.rag.entity.User;

/**
 * 登录响应：JWT + 用户信息（User.password 已 @JsonIgnore，不会泄露）
 */
public class LoginResponse {

    private String token;
    private User user;

    public LoginResponse(String token, User user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
