package com.dravya.setu.auth;

public class AuthResponse {

    private String token;
    private String tokenType;

    public AuthResponse() {
    }

    public AuthResponse(String token, String tokenType) {
        this.token = token;
        this.tokenType = tokenType;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}