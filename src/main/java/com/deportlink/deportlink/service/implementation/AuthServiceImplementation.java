package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.persistence.repository.UserRepository;
import com.deportlink.deportlink.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImplementation implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

}
