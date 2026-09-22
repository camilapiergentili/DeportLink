package com.deportlink.deportlink.infrastructure.adapter;
import com.deportlink.deportlink.application.port.out.InstructorLookupPort;
import com.deportlink.deportlink.model.entity.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
@RequiredArgsConstructor
public class InstructorLookupAdapter implements InstructorLookupPort {
    private final EntityManager em;
    public CourtPage courts(Long branchId, int page, int size) {
        var rows = em.createQuery("""
            SELECT c FROM CourtEntity c JOIN FETCH c.branch b
            WHERE c.activeStatus = com.deportlink.deportlink.enums.ActiveStatus.ACTIVE
            AND b.verificationStatus = com.deportlink.deportlink.enums.VerificationStatus.APPROVED
            AND (:branchId IS NULL OR b.id = :branchId) ORDER BY c.id
            """, CourtEntity.class).setParameter("branchId", branchId)
            .setFirstResult(Math.multiplyExact(page, size)).setMaxResults(size + 1).getResultList();
        return new CourtPage(rows.stream().limit(size).map(c -> new CourtOption(
            c.getId(), c.getName(), c.getBranch().getId(), c.getBranch().getName())).toList(), page, size, rows.size() > size);
    }
    public Optional<PlayerOption> playerByEmail(String email) {
        return em.createQuery("SELECT p FROM PlayerEntity p WHERE p.email = :email", PlayerEntity.class)
            .setParameter("email", email).getResultStream()
            .findFirst().map(p -> new PlayerOption(p.getId(), p.getFirstName() + " " + p.getLastName()));
    }
}
