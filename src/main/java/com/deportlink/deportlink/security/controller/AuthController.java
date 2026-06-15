package com.deportlink.deportlink.security.controller;

import com.deportlink.deportlink.model.entity.UserEntity;
import com.deportlink.deportlink.persistence.repository.UserRepository;
import com.deportlink.deportlink.security.config.JwtUtil;
import com.deportlink.deportlink.security.dto.AuthenticationRequest;
import com.deportlink.deportlink.security.dto.AuthenticationResponse;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@AllArgsConstructor
public class AuthController {
    private AuthenticationManager authenticationManager;
    private UserRepository userRepository;
    private JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthenticationRequest request){
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado en el sistema"));

        String jwt = jwtUtil.generateToken(user);

        return ResponseEntity.ok(new AuthenticationResponse(jwt));
    }
}
