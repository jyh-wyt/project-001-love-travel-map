package com.lovetravel.server.modules.auth.vo;

public class AuthResponse {

    private Long userId;
    private String account;
    private String nickname;

    public AuthResponse(Long userId, String account, String nickname) {
        this.userId = userId;
        this.account = account;
        this.nickname = nickname;
    }

    public Long getUserId() {
        return userId;
    }

    public String getAccount() {
        return account;
    }

    public String getNickname() {
        return nickname;
    }
}
