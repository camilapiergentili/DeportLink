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
import com.deportlink.deportlink.service.ClubAdminService;
import com.deportlink.deportlink.service.ClubOwnerService;
import com.deportlink.deportlink.service.ClubService;
import com.deportlink.deportlink.service.OwnerService;
import com.nimbusds.oauth2.sdk.util.CollectionUtils;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ClubServiceImplementation implements ClubService, ClubOwnerService, ClubAdminService {

    private final ClubRepository clubRepository;
    private final ClubMapper clubMapper;
    private final OwnerService ownerService;

    @Override
    @Transactional
    public ClubResponseDto create(ClubRequestDto clubDto){

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

    @Override
    @Transactional(readOnly = true)
    public ClubEntity getById(long id){
        return clubRepository.findById(id)
                .orElseThrow(() -> new ClubNotFoundException("El club no se encontro"));
    }

    @Override
    @Transactional(readOnly = true)
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

    @Override
    @Transactional(readOnly = true)
    public List<ClubResponseDto> getByActiveAndApproved(){
        return clubRepository
                .findByVerificationStatusAndActiveStatus(VerificationStatus.APPROVED, ActiveStatus.ACTIVE)
                .stream()
                .map(clubMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClubResponseDto> getAll(){
        List<ClubEntity> clubEntities = clubRepository.findAll();

        return clubEntities
                .stream()
                .map(clubMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(long id){
        ClubEntity clubEntity = getById(id);

        if(!clubEntity.getBranches().isEmpty()){
            throw new IllegalStateException("No se puede eliminar un club con sucursales activas");
        }

        clubRepository.delete(clubEntity);
    }

    @Override
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

    @Override
    @Transactional
    public void addOwner(long idClub, OwnerRequestDto ownerDto) throws OwnerAlreadyExistsException {
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

    @Override
    @Transactional
    public void deleteOwner(long idClub, long idOwner){
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

    @Override
    @Transactional
    public void deactivate(long idOwner, long idClub){
        activateAndDesactivateClub(idOwner, idClub, ActiveStatus.DESACTIVE);
    }

    @Override
    @Transactional
    public void activate(long idOwner, long idClub){
        activateAndDesactivateClub(idOwner, idClub, ActiveStatus.ACTIVE);
    }

    @Override
    @Transactional
    public void save(ClubEntity club){
        clubRepository.save(club);
    }

    @Override
    @Transactional
    public void approve(long idClub){
        modifyStatusClub(idClub, ActiveStatus.ACTIVE, VerificationStatus.APPROVED);
    }

    @Override
    @Transactional
    public void reject(long idClub){
        modifyStatusClub(idClub, ActiveStatus.DESACTIVE, VerificationStatus.REJECTED);
    }

    private void modifyStatusClub(long idClub, ActiveStatus activeStatus, VerificationStatus verificationStatus){
        ClubEntity clubEntity = getById(idClub);

        if(!clubEntity.getVerificationStatus().equals(VerificationStatus.PENDING)){
            throw new IllegalStateException("Solo se pueden aprobar/rechazar clubs en estado PENDING");
        }

        if(clubEntity.getActiveStatus().equals(activeStatus) &&
                clubEntity.getVerificationStatus().equals(verificationStatus)){
            throw new StatusAlreadyExistsException("El club ya se encuentra " + activeStatus + " y " + verificationStatus);
        }

        clubEntity.setActiveStatus(activeStatus);
        clubEntity.setVerificationStatus(verificationStatus);
        save(clubEntity);
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
