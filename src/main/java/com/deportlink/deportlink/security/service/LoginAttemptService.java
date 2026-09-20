package com.deportlink.deportlink.security.service;

import com.deportlink.deportlink.security.port.LoginAttemptPort;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService implements LoginAttemptPort {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record AttemptRecord(int count, Instant lockedUntil) {}

    // Estado en memoria del proceso — funciona correctamente con una sola instancia (la configuración
    // actual del despliegue en Railway). Si en el futuro se activan Replicas en Railway (más de una
    // instancia), este contador deja de ser efectivo porque cada instancia lleva su propio conteo por
    // separado — antes de subir réplicas, migrar a un store compartido (Redis o una tabla). Ver
    // auditoría del 20/08/2026 para el detalle del riesgo.
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