package com.deportlink.deportlink.security.advice;

import com.deportlink.deportlink.model.entity.UserMain;
import com.deportlink.deportlink.security.config.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class GlobalControllerAdvice {

    @ModelAttribute("id")
    public Long extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        UserMain userMain = (UserMain) auth.getPrincipal();
        return userMain.getId();
    }

    @ModelAttribute("role")
    public String extractRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        UserMain userMain = (UserMain) auth.getPrincipal();
        return userMain.getAuthorities().iterator().next().getAuthority();
    }
}