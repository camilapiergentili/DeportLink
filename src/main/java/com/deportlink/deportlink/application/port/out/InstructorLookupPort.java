package com.deportlink.deportlink.application.port.out;
import java.util.*;
public interface InstructorLookupPort {
    record CourtOption(Long courtId, String courtName, Long branchId, String branchName) {}
    record PlayerOption(Long playerId, String playerName) {}
    record CourtPage(List<CourtOption> items, int page, int size, boolean hasNext) {}
    CourtPage courts(Long branchId, int page, int size);
    Optional<PlayerOption> playerByEmail(String email);
}
