package com.graduation.rag.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 修改个人信息请求（白名单字段：仅 nickname/email，防 Mass Assignment 提权）
 */
public class UpdateProfileRequest {

    @Size(max = 50, message = "昵称过长")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
