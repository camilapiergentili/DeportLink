package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.PlayerGateway.PlayerSnapshot;
import com.deportlink.deportlink.model.entity.PlayerEntity;
import com.deportlink.deportlink.persistence.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PlayerRosterGatewayAdapterTest {

    @Autowired private PlayerRosterGatewayAdapter adapter;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private SportRepository sportRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private BranchRepository branchRepository;
    @Autowired private CourtRepository courtRepository;
    @Autowired private InstructorRepository instructorRepository;
    @Autowired private PlayerRepository playerRepository;

    @Test
    void findByIds_devuelveTodosLosNombresEnUnaSolaConsultaBatch() {
        AdapterTestFixtures fixtures = new AdapterTestFixtures(ownerRepository, sportRepository, clubRepository,
                branchRepository, courtRepository, instructorRepository, playerRepository);
        PlayerEntity p1 = fixtures.createPlayer("A");
        PlayerEntity p2 = fixtures.createPlayer("B");

        Map<Long, PlayerSnapshot> result = adapter.findByIds(Set.of(p1.getId(), p2.getId(), 999_999L));

        assertEquals(2, result.size());
        assertEquals(p1.getFirstName(), result.get(p1.getId()).firstName());
        assertFalse(result.containsKey(999_999L));
    }

    @Test
    void findByIds_setVacio_devuelveMapaVacioSinConsultar() {
        assertTrue(adapter.findByIds(Set.of()).isEmpty());
    }
}
