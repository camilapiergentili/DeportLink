package com.deportlink.deportlink.security.advice;

import com.deportlink.deportlink.security.config.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
@AllArgsConstructor
public class GlobalControllerAdvice {

    private final JwtUtil jwtUtil;

    @ModelAttribute("id")
    public Long extractUserIdFromToken(HttpServletRequest request) {

        String tokenLimpio = extraerToken(request);
        return jwtUtil.extractUserId(tokenLimpio);
    }

    @ModelAttribute("role")
    public String extractRoleFromToken(HttpServletRequest request) {

        String tokenLimpio = extraerToken(request);
        return jwtUtil.extractRole(tokenLimpio);
    }

    private String extraerToken(HttpServletRequest request){
        if (isPublicRoute(request)) {
            return null;
        }

        String authHeader = request.getHeader("Authorization");

        return jwtUtil.resolveToken(authHeader);
    }

    // El método inteligente que analiza la ruta y el método HTTP (POST, GET, etc.)
    private boolean isPublicRoute(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();

        // 1. Todo lo que sea Login / Autenticación es público
        if (path.startsWith("/api/auth")) return true;

        // 2. SI ES UN POST A PLAYERS (REGISTRO), ¡ES PÚBLICO!
        if (path.startsWith("/api/players") && method.equals("POST")) return true;

        // 3. SI ES UN POST A OWNERS (REGISTRO), ¡ES PÚBLICO!
        if (path.startsWith("/api/owners") && method.equals("POST")) return true;

        // 4. Documentación de Swagger
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) return true;

        return false;
    }
}