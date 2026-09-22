package com.deportlink.deportlink.application.usecase.instructor;
import com.deportlink.deportlink.application.port.out.InstructorLookupPort;
import com.deportlink.deportlink.exception.PlayerNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InstructorLookupUseCase {
    private final InstructorLookupPort lookup;
    public InstructorLookupPort.CourtPage courts(Long branchId, int page, int size) {
        return lookup.courts(branchId, page, size);
    }
    public InstructorLookupPort.PlayerOption player(String email) {
        return lookup.playerByEmail(email).orElseThrow(() -> new PlayerNotFoundException("No se encontró el jugador"));
    }
}
