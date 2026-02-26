package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.ClubRequestDto;
import com.deportlink.deportlink.dto.request.OwnerRequestDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.dto.response.OwnerResponseDto;
import com.deportlink.deportlink.exception.*;
import com.deportlink.deportlink.mapper.ClubMapper;
import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.VerificationStatus;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.model.entity.OwnerEntity;
import com.deportlink.deportlink.persistence.repository.ClubRepository;
import com.deportlink.deportlink.service.ClubService;
import com.deportlink.deportlink.service.OwnerService;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ClubServiceImplementation implements ClubService {

    private final ClubRepository clubRepository;
    private final ClubMapper clubMapper;
    private final OwnerService ownerService;

    @Transactional
    public ClubResponseDto createClub(ClubRequestDto clubDto){

        if (CollectionUtils.isEmpty(clubDto.getOwnerIds())) {
            throw new IllegalArgumentException("El club debe estar asociado a al menos un dueño");
        }

        Set<OwnerEntity> owners = clubDto.getOwnerIds().
                stream().
                map(ownerService::getById).
                collect(Collectors.toSet());

        ClubEntity clubEntity = clubMapper.toModel(clubDto);

        if(clubRepository.findByCuit(clubEntity.getCuit()).isPresent()){
            throw new ClubAlreadyExistsException("El club con el número de CUIT " + clubEntity.getCuit() + " ya se encuentra registrado");
        }

        if(clubRepository.findByLegalName(clubEntity.getLegalName()).isPresent()){
            throw new ClubAlreadyExistsException("El club con el nombre " + clubEntity.getLegalName() + " ya se encuentra registrado");
        }

        clubEntity.setVerificationStatus(VerificationStatus.PENDING);
        clubEntity.setActiveStatus(ActiveStatus.DESACTIVE);
        clubEntity.setOwners(owners);
        owners.forEach(owner -> owner.getClubs().add(clubEntity));

        save(clubEntity);

        return clubMapper.toResponse(clubEntity);
    }

    public ClubEntity getById(long id){
        return clubRepository.findById(id)
                .orElseThrow(() -> new ClubNotFoundException("El club no se encontro"));
    }

    public ClubResponseDto getByIdResponse(long id) {
        ClubEntity clubEntity = getById(id);

        switch (clubEntity.getVerificationStatus()) {
            case PENDING -> throw new ClubNotApprovedException("El club no se puede mostrar, la documentación está siendo revisada");
            case REJECTED -> throw new ClubNotApprovedException("El club no ha pasado la verificación de documentación, contactese con servicio técnico");
            default -> {}
        }

        if(clubEntity.getActiveStatus().equals(ActiveStatus.DESACTIVE)){
            throw new ClubNotActivedException("El club no se encuentra activo");
        }

        return clubMapper.toResponse(clubEntity);
    }

    public List<ClubResponseDto> getAllClubsActiveAndApproved(){
         return clubRepository.findAll()
                 .stream()
                 .filter(club -> club.getVerificationStatus() == VerificationStatus.APPROVED
                         && club.getActiveStatus() == ActiveStatus.ACTIVE)
                 .map(clubMapper::toResponse)
                 .collect(Collectors.toList());
    }

    public List<ClubResponseDto> getAll(){
        List<ClubEntity> clubEntities = clubRepository.findAll();

        return clubEntities
                .stream()
                .map(clubMapper::toResponse)
                .collect(Collectors.toList());
    }

    public void delete(long id){
        ClubEntity clubEntity = getById(id);

        clubRepository.delete(clubEntity);
    }

    @Transactional
    public void update(long id, ClubRequestDto clubDto){
        ClubEntity clubEntity = getById(id);

        boolean requiresReview = !clubEntity.getLegalName().equals(clubDto.getLegalName()) ||
                !clubEntity.getCuit().equals(clubDto.getCuit()) ||
                !clubEntity.getClubType().equals(clubDto.getClubType());

        clubEntity.setName(clubDto.getName());
        clubEntity.setLegalName(clubDto.getLegalName());
        clubEntity.setClubType(clubDto.getClubType());
        clubEntity.setCuit(clubDto.getCuit());

        if(requiresReview) {
            clubEntity.setVerificationStatus(VerificationStatus.PENDING);
            clubEntity.setActiveStatus(ActiveStatus.DESACTIVE);
        }

        save(clubEntity);
    }

    @Transactional
    public void addOwnerToClub(long idClub, OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException {
        ClubEntity clubEntity = getById(idClub);
        OwnerResponseDto ownerResponse = ownerService.register(ownerDto);
        OwnerEntity ownerEntity = ownerService.getById(ownerResponse.getId());

        Set<OwnerEntity> clubEntityOwners = clubEntity.getOwners();

        boolean exists = clubEntityOwners.stream().
                anyMatch(o -> o.getCuil().equals(ownerEntity.getCuil()));

        if(exists){
            throw new OwnerAlreadyExistsException("El dueño ya esta como propietario del club");
        }

        clubEntity.getOwners().add(ownerEntity);
        ownerEntity.getClubs().add(clubEntity);

        save(clubEntity);
    }

    @Transactional
    public void deleteOwnerToClub(long idClub, long idOwner){
        ClubEntity clubEntity = getById(idClub);
        OwnerEntity ownerEntity = ownerService.getById(idOwner);

        boolean existsOwner = clubEntity.getOwners()
                .stream()
                .anyMatch(o -> o.getCuil().equals(ownerEntity.getCuil()));

        if(!existsOwner){
            throw new OwnerNotInClubException("La persona que quieres eliminar, no pertenece al club");
        }

        clubEntity.getOwners().remove(ownerEntity);
        ownerEntity.getClubs().remove(clubEntity);

        save(clubEntity);
    }

    public void deactivateClub(long idOwner, long idClub){
        activateAndDesactivateClub(idOwner, idClub, ActiveStatus.DESACTIVE);
    }

    public void activateClub(long idOwner, long idClub){
        activateAndDesactivateClub(idOwner, idClub, ActiveStatus.ACTIVE);
    }

    public void save(ClubEntity club){
        clubRepository.save(club);
    }

    private void activateAndDesactivateClub(long idOwner, long idClub, ActiveStatus status){
        OwnerEntity owner = ownerService.getById(idOwner);
        List<ClubEntity> clubsByOwner = owner.getClubs();

        ClubEntity club = clubsByOwner.stream()
                .filter(o -> o.getId() == idClub)
                .findFirst()
                .orElseThrow(() -> new ClubNotFoundException("La persona no tiene el club asociado"));

        if(!club.getVerificationStatus().equals(VerificationStatus.APPROVED)){
            throw new ClubNotApprovedException(
                    "El club no se encuentra aprobado para poder usar la función de activar y desactivar club");
        }

        if(club.getActiveStatus().equals(status)){
            throw new IllegalStateException("El club ya está en el estado solicitado");
        }

        club.setActiveStatus(status);
        save(club);
    }

}
