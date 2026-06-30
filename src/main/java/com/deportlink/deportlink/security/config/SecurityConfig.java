package com.deportlink.deportlink.security.config;

import com.deportlink.deportlink.security.service.JwtFilter;
import com.deportlink.deportlink.security.service.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@AllArgsConstructor
public class SecurityConfig {
    private final UserDetailsServiceImpl userDetailsService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Desactivamos CSRF porque JWT no usa cookies
                .csrf(AbstractHttpConfigurer::disable)

                // 1. Agregamos el CORS que ya tenías cocinado (adaptá la URL si cambió tu frontend)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Le decimos a Spring que no guarde sesiones en el servidor.
                // Cada petición web debe ser independiente y traer su propio token JWT.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Configuración de las reglas de acceso a las rutas
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas (Cualquiera puede entrar sin token)
                        .requestMatchers("/api/auth/**").permitAll() // El login
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/players", "/api/owners").permitAll() // El registro de usuarios
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll() // Documentación de Swagger (si usás)

                        // Todo el resto de la aplicación SÍ O SÍ va a requerir que el usuario esté logueado
                        .anyRequest().authenticated()
                )

                //Tus manejadores de errores en JSON
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )

                // Le pasamos el proveedor de autenticación que configuraste abajo
                .authenticationProvider(authenticationProvider())

                // Le decimos a Spring que ejecute tu "JwtFilter"
                // ANTES del filtro de usuario y contraseña por defecto de Spring.
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
    // Este Bean es el que le enseña a Spring cómo verificar las contraseñas del Login
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // Le decimos a Spring que use nuestro buscador de usuarios y nuestro encriptador
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    // --- TUS MANEJADORES DE ERROR REUTILIZADOS ---
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Debes iniciar sesión para acceder a este recurso\"}");
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setContentType("application/json");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\": \"No tienes permisos para acceder a este recurso\"}");
        };
    }

    // --- TU CONFIGURACIÓN DE CORS REUTILIZADA ---
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:5173" // Podés agregar localhost para tus pruebas de Frontend (React/Vite)
                //""
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
