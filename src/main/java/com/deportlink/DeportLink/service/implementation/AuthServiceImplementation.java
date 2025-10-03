package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.request.UserRequestDto;
import com.deportlink.DeportLink.persistence.repository.UserRepository;
import com.deportlink.DeportLink.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImplementation implements AuthService {

    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    public void register(UserRequestDto userRequestDto){

    }

}
