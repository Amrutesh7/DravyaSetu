package com.dravya.setu.auth;

public class AuthResponse {

    private String token;
    private String tokenType;
    private long expiresIn;

    public AuthResponse() {
    }

    public AuthResponse(String token, String tokenType) {
        this(token, tokenType, 0);
    }

    public AuthResponse(String token, String tokenType, long expiresIn) {
        this.token = token;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    /** Access-token lifetime in seconds, so clients can refresh before it expires. */
    public long getExpiresIn() {
        return expiresIn;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
