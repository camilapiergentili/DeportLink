package com.deportlink.deportlink.security.port;

public interface LoginAttemptPort {
    boolean isBlocked(String ip);
    void registerFailure(String ip);
    void registerSuccess(String ip);
}