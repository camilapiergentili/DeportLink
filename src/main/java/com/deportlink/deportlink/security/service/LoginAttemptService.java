package com.deportlink.deportlink.security.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record AttemptRecord(int count, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String ip) {
        AttemptRecord record = attempts.get(ip);
        if (record == null || record.lockedUntil() == null) return false;
        if (Instant.now().isBefore(record.lockedUntil())) return true;
        attempts.remove(ip);
        return false;
    }

    public void registerFailure(String ip) {
        attempts.merge(ip, new AttemptRecord(1, null), (existing, ignored) -> {
            int newCount = existing.count() + 1;
            Instant lockUntil = newCount >= MAX_ATTEMPTS ? Instant.now().plus(LOCK_DURATION) : null;
            return new AttemptRecord(newCount, lockUntil);
        });
    }

    public void registerSuccess(String ip) {
        attempts.remove(ip);
    }
}