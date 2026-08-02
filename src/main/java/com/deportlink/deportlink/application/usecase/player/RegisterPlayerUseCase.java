package com.deportlink.deportlink.application.usecase.player;

import com.deportlink.deportlink.domain.model.Player;
import com.deportlink.deportlink.domain.model.PlayerAddress;
import com.deportlink.deportlink.domain.port.out.PlayerRepositoryPort;
import com.deportlink.deportlink.dto.request.AddressRequestDto;
import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.exception.PlayerAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterPlayerUseCase {

    private final PlayerRepositoryPort playerRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Player execute(PlayerRequestDto dto) {
        log.info("Registering player: email={}", dto.getEmail());

        if (playerRepository.existsByEmail(dto.getEmail())) {
            throw new PlayerAlreadyExistsException("El email " + dto.getEmail() + " ya se encuentra registrado");
        }

        String encodedPassword = passwordEncoder.encode(dto.getPassword());

        Set<PlayerAddress> addresses = Set.of();
        AddressRequestDto addrDto = dto.getAddressRequestDto();
        if (addrDto != null) {
            addresses = Set.of(new PlayerAddress(null, addrDto.getStreetName(), addrDto.getNumber(),
                    addrDto.getCity(), addrDto.getProvince(), addrDto.getPostalCode(),
                    addrDto.getLatitude(), addrDto.getLongitude(), true));
        }

        Player player = new Player(null, dto.getFirstName(), dto.getLastName(),
                dto.getEmail(), dto.getPhone(), encodedPassword, addresses);

        Player saved = playerRepository.save(player);
        log.info("Player registered: playerId={}", saved.id());
        return saved;
    }
}