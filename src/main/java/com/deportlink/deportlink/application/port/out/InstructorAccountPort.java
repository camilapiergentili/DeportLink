package com.deportlink.deportlink.application.port.out;
import java.util.Optional;
import com.deportlink.deportlink.model.Rol;
public interface InstructorAccountPort {
    record Profile(Long id, String firstName, String lastName, String email, String phone, Rol role) {}
    Optional<Profile> findUser(Long id);
    Optional<Profile> findInstructor(Long id);
    boolean emailExists(String email);
    Profile create(String firstName, String lastName, String email, String encodedPassword, String phone);
}
