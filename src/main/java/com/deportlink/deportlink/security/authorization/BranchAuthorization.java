package com.deportlink.deportlink.security.authorization;

import com.deportlink.deportlink.model.entity.UserMain;
import com.deportlink.deportlink.persistence.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("branchAuthorization")
@RequiredArgsConstructor
public class BranchAuthorization {

    private final BranchRepository branchRepository;

    public boolean isOwnerOfBranch(long idBranch, Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserMain user)) {
            return false;
        }

        return branchRepository.existsByIdAndClub_Owners_Id(
                idBranch,
                user.getId()
        );
    }
}
