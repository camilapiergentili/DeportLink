package com.deportlink.deportlink.security.service;

import com.deportlink.deportlink.security.config.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

//lee la cabecera, limpia el token, extrae el email, busca al usuario en la base de datos
// y lo mete en el contexto de Spring.
@Component
@AllArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private JwtUtil jwtUtil;
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        // De la request obtengo el Header del token
        String header = request.getHeader("Authorization");

        // Limpio el Bearer y me quedo solo con el token
        String token = jwtUtil.resolveToken(header);

        //Si no es nullo o ya expero, extraemos el mail del token y nos fijamos que exista en la base de datos
        if (token != null && !jwtUtil.isTokenExpired(token)
                && SecurityContextHolder.getContext().getAuthentication() == null){

            String email = jwtUtil.extractUsername(token);

            UserDetails user = userDetailsService.loadUserByUsername(email);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            user,                    // quién es
                            null,                    // credenciales (no necesarias acá)
                            user.getAuthorities()    // sus permisos (ROLE_PLAYER)
                    );

            // Guardamos los detalles web (como la IP) en la credencial
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);

    }
}
