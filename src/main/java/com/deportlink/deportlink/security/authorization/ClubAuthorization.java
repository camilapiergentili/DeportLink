package com.deportlink.deportlink.security.authorization;

import com.deportlink.deportlink.model.entity.UserMain;
import com.deportlink.deportlink.persistence.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("clubAuthorization")
@RequiredArgsConstructor
public class ClubAuthorization {

    private final ClubRepository clubRepository;

    public boolean isOwnerOfClub(long idClub, Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserMain user)) {
            return false;
        }

        return clubRepository.existsByIdAndOwners_Id(
                idClub,
                user.getId()
        );
    }
}
