package com.deportlink.deportlink.security.advice;

import com.deportlink.deportlink.security.config.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

//Esta clase se ejecuta antes de que cada peticion llegue al controller,
// Obtiene el token de las rutas que requieren autorizacion,
//Extrae antes de cada peticion el rol y el id para verificar permisos
// y no pedir el usurio y contraseña cada vez que quiera entrar a una ruta protegida
@ControllerAdvice
@AllArgsConstructor
public class GlobalControllerAdvice {

    private final JwtUtil jwtUtil;

    @ModelAttribute("id")
    public Long extractUserIdFromToken(HttpServletRequest request) {

        String tokenLimpio = extraerToken(request);
        return (tokenLimpio != null) ? jwtUtil.extractUserId(tokenLimpio) : null;
    }

    @ModelAttribute("role")
    public String extractRoleFromToken(HttpServletRequest request) {
        String token = extraerToken(request);
        return (token != null) ? jwtUtil.extractRole(token) : null;
    }

    private String extraerToken(HttpServletRequest request){
        // 1. Le preguntamos a Spring Security: ¿Ya autenticaste a alguien en esta petición?
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // 2. Si no hay nadie autenticado, o es un usuario "Anónimo", significa que es una RUTA PÚBLICA.
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null; // No buscamos token
        }

        // 3. Si no era pública, entonces sí sacamos y limpiamos el token de la cabecera
        String authHeader = request.getHeader("Authorization");
        return jwtUtil.resolveToken(authHeader);
    }

}