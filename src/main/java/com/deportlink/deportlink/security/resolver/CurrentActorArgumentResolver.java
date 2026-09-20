package com.deportlink.deportlink.security.resolver;
import com.deportlink.deportlink.application.actor.*;
import com.deportlink.deportlink.model.entity.UserMain;
import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.*;
@Component
public class CurrentActorArgumentResolver implements HandlerMethodArgumentResolver {
    public boolean supportsParameter(MethodParameter p) {
        return p.hasParameterAnnotation(CurrentActor.class) && p.getParameterType() == Actor.class;
    }
    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m,
            NativeWebRequest r, WebDataBinderFactory b) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserMain user))
            throw new AccessDeniedException("Identidad no disponible");
        var role = auth.getAuthorities().stream().map(a -> a.getAuthority())
                .filter(a -> a.equals("ROLE_INSTRUCTOR") || a.equals("ROLE_ADMIN"))
                .findFirst().orElseThrow(() -> new AccessDeniedException("Rol no permitido"));
        return new Actor(user.getId(), ActorRole.valueOf(role.substring(5)));
    }
}
