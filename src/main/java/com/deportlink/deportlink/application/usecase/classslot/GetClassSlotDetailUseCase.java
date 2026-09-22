package com.deportlink.deportlink.application.usecase.classslot;
import com.deportlink.deportlink.application.actor.Actor;
import com.deportlink.deportlink.application.port.out.PlayerRosterGateway;
import com.deportlink.deportlink.domain.model.*;
import com.deportlink.deportlink.domain.port.out.*;
import com.deportlink.deportlink.exception.ClassSlotNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
public class GetClassSlotDetailUseCase {
    private final ClassSlotRepositoryPort slots;
    private final ClassEnrollmentRepositoryPort enrollments;
    private final PlayerRosterGateway players;
    public record Member(Long playerId, String playerName, boolean active) {}
    public record Detail(ClassSlot slot, List<Member> players) {}
    @Transactional(readOnly = true)
    public Detail execute(Actor actor, Long slotId) {
        var slot = slots.findById(slotId).filter(s -> actor.canAccess(s.instructorId()))
                .orElseThrow(() -> new ClassSlotNotFoundException("No se encontró el horario"));
        var members = enrollments.findActiveByClassSlotId(slotId);
        var names = players.findByIds(members.stream().map(ClassEnrollment::playerId).collect(Collectors.toSet()));
        return new Detail(slot, members.stream().sorted(Comparator.comparing(ClassEnrollment::playerId)).map(e -> {
            var p = names.get(e.playerId());
            return new Member(e.playerId(), p == null ? null : p.firstName() + " " + p.lastName(), true);
        }).toList());
    }
}
