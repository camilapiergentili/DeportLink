package com.deportlink.deportlink.security.service;

import com.deportlink.deportlink.exception.TooManyRequestsException;
import com.deportlink.deportlink.exception.UserNotFoundException;
import com.deportlink.deportlink.model.entity.UserEntity;
import com.deportlink.deportlink.persistence.repository.UserRepository;
import com.deportlink.deportlink.security.config.JwtUtil;
import com.deportlink.deportlink.security.dto.AuthenticationRequest;
import com.deportlink.deportlink.security.dto.AuthenticationResponse;
import com.deportlink.deportlink.security.port.AuthServicePort;
import com.deportlink.deportlink.security.port.LoginAttemptPort;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService implements AuthServicePort {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final LoginAttemptPort loginAttemptService;

    public AuthenticationResponse login(AuthenticationRequest request, String ip) {
        if (loginAttemptService.isBlocked(ip)) {
            throw new TooManyRequestsException("Demasiados intentos fallidos. Intentá de nuevo en 15 minutos.");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.registerFailure(ip);
            throw e;
        }

        loginAttemptService.registerSuccess(ip);

        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));

        String jwt = jwtUtil.generateToken(user);
        return new AuthenticationResponse(jwt);
    }
}
