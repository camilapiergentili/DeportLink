package com.deportlink.deportlink.security.authorization;

import com.deportlink.deportlink.model.entity.UserMain;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("courtAuthorization")
@RequiredArgsConstructor
public class CourtAuthorization {

    private final CourtRepository courtRepository;

    public boolean isOwnerOfCourt(long idCourt, Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserMain user)) {
            return false;
        }

        return courtRepository.existsByCourtAndOwner(idCourt, user.getId());
    }
}