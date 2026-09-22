package com.deportlink.deportlink.infrastructure.adapter;
import com.deportlink.deportlink.application.port.out.InstructorAccountPort;
import com.deportlink.deportlink.model.Rol;
import com.deportlink.deportlink.model.entity.*;
import com.deportlink.deportlink.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Optional;
@Component
@RequiredArgsConstructor
public class InstructorAccountAdapter implements InstructorAccountPort {
    private final UserRepository users;
    private final InstructorRepository instructors;
    public Optional<Profile> findUser(Long id) { return users.findById(id).map(this::profile); }
    public Optional<Profile> findInstructor(Long id) { return instructors.findById(id).map(this::profile); }
    public boolean emailExists(String email) { return users.findByEmail(email).isPresent(); }
    public Profile create(String firstName, String lastName, String email, String encodedPassword, String phone) {
        var entity = new InstructorEntity();
        entity.setFirstName(firstName); entity.setLastName(lastName);
        entity.setEmail(email); entity.setPassword(encodedPassword); entity.setPhone(phone);
        entity.setRole(Rol.INSTRUCTOR);
        return profile(instructors.saveAndFlush(entity));
    }
    private Profile profile(UserEntity u) {
        return new Profile(u.getId(), u.getFirstName(), u.getLastName(), u.getEmail(), u.getPhone(), u.getRole());
    }
}
