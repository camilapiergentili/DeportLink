package com.deportlink.deportlink.security.config;

import com.deportlink.deportlink.security.advice.JwtAccessDeniedHandler;
import com.deportlink.deportlink.security.advice.JwtAuthenticationEntryPoint;
import com.deportlink.deportlink.security.service.JwtFilter;
import com.deportlink.deportlink.security.service.UserDetailsServiceImpl;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtFilter jwtFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    // Sin default hardcodeado a propósito: si CORS_ALLOWED_ORIGINS no está seteada (o queda
    // vacía), cors.allowed-origins resuelve a un string vacío — ver application.properties —
    // y esto termina en una lista vacía, no en un valor de desarrollo colado por accidente.
    @Value("${cors.allowed-origins}")
    private List<String> corsAllowedOrigins;

    /**
     * Falla el arranque con un mensaje claro si corsAllowedOrigins quedó vacío — ya sea porque
     * CORS_ALLOWED_ORIGINS no está seteada o porque quedó seteada pero en blanco. Sin este
     * chequeo, la app subiría igual (una lista vacía es una configuración válida para
     * CorsConfiguration, solo que bloquea todo el tráfico cross-origin) y el problema recién
     * se notaría en producción, con el frontend fallando y sin ningún mensaje que apunte a la
     * causa real.
     */
    @PostConstruct
    void validateCorsAllowedOrigins() {
        boolean isBlank = corsAllowedOrigins == null
                || corsAllowedOrigins.isEmpty()
                || corsAllowedOrigins.stream().allMatch(String::isBlank);

        if (isBlank) {
            throw new IllegalStateException(
                    "CORS_ALLOWED_ORIGINS no está seteada, o quedó vacía/mal configurada. "
                            + "Verificá esa variable de entorno antes de levantar la app "
                            + "(ej: CORS_ALLOWED_ORIGINS=https://miapp.com,https://admin.miapp.com). "
                            + "En desarrollo local, activá el perfil 'dev' en su lugar "
                            + "(--spring.profiles.active=dev), que ya trae configurado "
                            + "http://localhost:5173."
            );
        }
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Desactivamos CSRF porque JWT no usa cookies
                .csrf(AbstractHttpConfigurer::disable)

                // Configuración de CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Sin sesiones — cada request trae su propio JWT
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Reglas de acceso a las rutas
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/players").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .anyRequest().authenticated()
                )

                // Manejadores de error usando las clases separadas
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
