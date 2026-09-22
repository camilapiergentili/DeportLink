package com.deportlink.deportlink.infrastructure.adapter;

import com.deportlink.deportlink.application.port.out.InstructorGateway.InstructorSnapshot;
import com.deportlink.deportlink.model.entity.InstructorEntity;
import com.deportlink.deportlink.persistence.repository.InstructorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstructorGatewayAdapterTest {

    @Autowired private InstructorGatewayAdapter adapter;
    @Autowired private InstructorRepository instructorRepository;

    @Test
    void findById_instructorExistente_loEncuentra() {
        InstructorEntity instructor = new InstructorEntity();
        instructor.setEmail("instructor-" + System.nanoTime() + "@example.com");
        instructor.setPassword("irrelevant");
        instructor.setFirstName("Juan");
        instructor.setLastName("Profesor");
        instructor = instructorRepository.save(instructor);

        Optional<InstructorSnapshot> result = adapter.findById(instructor.getId());

        assertTrue(result.isPresent());
        assertEquals(instructor.getId(), result.get().id());
    }

    @Test
    void findById_instructorInexistente_devuelveVacio() {
        assertTrue(adapter.findById(999_999L).isEmpty());
    }
}
