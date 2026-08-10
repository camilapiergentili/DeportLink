package com.deportlink.deportlink.security.port;

import com.deportlink.deportlink.security.dto.AuthenticationRequest;
import com.deportlink.deportlink.security.dto.AuthenticationResponse;

public interface AuthServicePort {
    AuthenticationResponse login(AuthenticationRequest request, String ip);
}