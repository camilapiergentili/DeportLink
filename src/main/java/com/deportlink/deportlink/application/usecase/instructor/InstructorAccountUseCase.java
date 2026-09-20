package com.deportlink.deportlink.application.usecase.instructor;
import com.deportlink.deportlink.application.port.out.InstructorAccountPort;
import com.deportlink.deportlink.application.port.out.InstructorAccountPort.Profile;
import com.deportlink.deportlink.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class InstructorAccountUseCase {
    private final InstructorAccountPort accounts;
    private final PasswordEncoder encoder;
    @Transactional
    public Profile register(String firstName, String lastName, String email, String password, String phone) {
        if (accounts.emailExists(email))
            throw new AccountAlreadyExistsException("El email ya se encuentra registrado");
        return accounts.create(firstName, lastName, email, encoder.encode(password), phone);
    }
    @Transactional(readOnly = true)
    public Profile instructor(Long id) {
        return accounts.findInstructor(id).orElseThrow(() -> new InstructorNotFoundException("No se encontró el instructor"));
    }
    @Transactional(readOnly = true)
    public Profile user(Long id) {
        return accounts.findUser(id).orElseThrow(() -> new UserNotFoundException("Usuario no encontrado"));
    }
}
