package com.deportlink.deportlink.security.service;

import com.deportlink.deportlink.exception.UserNotFoundException;
import com.deportlink.deportlink.model.entity.UserEntity;
import com.deportlink.deportlink.persistence.repository.UserRepository;
import com.deportlink.deportlink.security.config.JwtUtil;
import com.deportlink.deportlink.security.dto.AuthenticationRequest;
import com.deportlink.deportlink.security.dto.AuthenticationResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public AuthenticationResponse login(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));

        String jwt = jwtUtil.generateToken(user);
        return new AuthenticationResponse(jwt);
    }
}
