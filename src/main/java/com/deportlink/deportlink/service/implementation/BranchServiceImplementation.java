package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.BranchRequestDto;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import com.deportlink.deportlink.exception.*;
import com.deportlink.deportlink.mapper.AddressMapper;
import com.deportlink.deportlink.mapper.BranchMapper;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.model.entity.AddressEntity;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.persistence.repository.BranchRepository;
import com.deportlink.deportlink.service.BranchAdminService;
import com.deportlink.deportlink.service.BranchOwnerService;
import com.deportlink.deportlink.service.BranchService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class BranchServiceImplementation implements BranchService, BranchOwnerService, BranchAdminService {

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;
    private final AddressMapper addressMapper;
    private final ClubServiceImplementation clubService;

    @Override
    @Transactional
    public void create(BranchRequestDto branchDto){
        log.info("Creating branch: name={}, clubId={}", branchDto.getName(), branchDto.getIdClub());

        ClubEntity clubEntity = clubService.getById(branchDto.getIdClub());

        if(clubEntity.getVerificationStatus() != VerificationStatus.APPROVED){
            throw new ClubNotApprovedException("El club " + clubEntity.getLegalName() + " aun no esta autorizado para agregar sucursales");
        }

        BranchEntity branchEntity = branchMapper.toModel(branchDto);

        validateBranchExists(branchEntity, clubEntity, branchDto.getName());

        branchEntity.setClub(clubEntity);
        branchEntity.setVerificationStatus(VerificationStatus.PENDING);
        branchEntity.setActiveStatus(ActiveStatus.DESACTIVE);
        clubEntity.getBranches().add(branchEntity);

        save(branchEntity);
        log.info("Branch created successfully: branchId={}", branchEntity.getId());
    }

    @Transactional
    public void save(BranchEntity branchEntity){
        branchRepository.save(branchEntity);
    }

    @Transactional(readOnly = true)
    public BranchEntity getById(long id){
        return branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("No se encontro registro de la cancha"));
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponseDto getByIdResponse(long id){
        BranchEntity branchEntity = getById(id);
        return branchMapper.toResponse(branchEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponseDto> getAllActiveAndApproved(long idClub){

        List<BranchEntity> branchesEntity = branchRepository.
                findActiveAndApprovedByClubId(idClub,
                ActiveStatus.ACTIVE,
                VerificationStatus.APPROVED);

        if(branchesEntity.isEmpty()){
            throw new BranchNotFoundException("El club no tiene sucursales activas y aprobadas");
        }

        return branchesEntity.stream()
                .map(branchMapper::toResponse)
                .toList();

    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponseDto> getAll(long idClub){

        clubService.getById(idClub);
        List<BranchEntity> branchesEntity = branchRepository.findAllByClubId(idClub);

        return branchesEntity.stream()
                .map(branchMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponseDto getByIdApproved(long idBranch){
        BranchEntity branchEntity = getById(idBranch);

        switch (branchEntity.getVerificationStatus()) {
            case PENDING -> throw new BranchNotApprovedException("La sucursal no se puede mostrar, la documentación está siendo revisada");
            case REJECTED -> throw new BranchNotApprovedException("La sucursal no ha pasado la verificación de documentación, contactese con servicio técnico");
            default -> {}
        }

        if(branchEntity.getActiveStatus().equals(ActiveStatus.DESACTIVE)){
            throw new BranchNotActivedExcepcion("La sucursal se encuentra inactiva");
        }

        return branchMapper.toResponse(branchEntity);
    }

    @Override
    @Transactional
    public void delete(long id){
        log.info("Deleting branch: branchId={}", id);

        BranchEntity branchEntity = getById(id);
        branchRepository.delete(branchEntity);

        log.info("Branch deleted successfully: branchId={}", id);
    }

    @Override
    @Transactional
    public void update(long id, BranchRequestDto branchDto){
        log.info("Updating branch: branchId={}, name={}", id, branchDto.getName());

        BranchEntity branchEntity = getById(id);
        AddressEntity newAddress = addressMapper.toModel(branchDto.getAddressRequestDto());

        boolean requiresReview = !branchEntity.getAddress().equals(newAddress);

        branchEntity.setName(branchDto.getName());
        branchEntity.setAddress(newAddress);

        if(requiresReview){
            branchEntity.setVerificationStatus(VerificationStatus.PENDING);
            branchEntity.setActiveStatus(ActiveStatus.DESACTIVE);
        }

        save(branchEntity);

        log.info("Branch updated successfully: branchId={}", branchEntity.getId());
    }

    @Override
    @Transactional
    public void desactive(long idBranch, long idClub){
        log.info("Deactivating branch: branchId={}, clubId={}", idBranch, idClub);
        activateAndDesactivateBranchByClub(idBranch, idClub, ActiveStatus.DESACTIVE);
        log.info("Branch deactivated successfully: branchId={}", idBranch);
    }

    @Override
    @Transactional
    public void active(long idBranch, long idClub){
        log.info("Activating branch: branchId={}, clubId={}", idBranch, idClub);
        activateAndDesactivateBranchByClub(idBranch, idClub, ActiveStatus.ACTIVE);
        log.info("Branch activated successfully: branchId={}", idBranch);
    }

    @Override
    @Transactional
    public void approve(long idBranch){
        log.info("Approving branch: branchId={}", idBranch);
        modifyStatus(idBranch, ActiveStatus.ACTIVE, VerificationStatus.APPROVED);
        log.info("Branch approved successfully: branchId={}", idBranch);
    }

    @Override
    @Transactional
    public void reject(long idBranch){
        log.info("Rejecting branch: branchId={}", idBranch);
        modifyStatus(idBranch, ActiveStatus.DESACTIVE, VerificationStatus.REJECTED);
        log.info("Branch rejected successfully: branchId={}", idBranch);
    }

    private void modifyStatus(long idBranch, ActiveStatus activeStatus, VerificationStatus verificationStatus){
        BranchEntity branchEntity = getById(idBranch);

        if(!branchEntity.getVerificationStatus().equals(VerificationStatus.PENDING)){
            throw new IllegalStateException("Solo se pueden aprobar/rechazar clubs en estado PENDING");
        }

        if(branchEntity.getActiveStatus().equals(activeStatus) &&
                branchEntity.getVerificationStatus().equals(verificationStatus)){
            throw new StatusAlreadyExistsException("El club ya se encuentra " + activeStatus + " y " + verificationStatus);
        }

        branchEntity.setActiveStatus(activeStatus);
        branchEntity.setVerificationStatus(verificationStatus);
        save(branchEntity);
    }


    private void activateAndDesactivateBranchByClub(long idBranch, long idClub, ActiveStatus status){
        ClubEntity clubEntity = clubService.getById(idClub);

        BranchEntity branchEntity = clubEntity.getBranches()
                .stream()
                .filter(b -> b.getId() == idBranch)
                .findFirst()
                .orElseThrow(() -> new BranchNotFoundException("No existe sucursal que desea desactivar"));

        if(!branchEntity.getVerificationStatus().equals(VerificationStatus.APPROVED)){
            throw new BranchNotApprovedException(
                    "La sucursal no se encuentra aprobada para poder usar la función de activar y desactivar sucursal");
        }

        if(branchEntity.getActiveStatus().equals(status)){
            throw new IllegalStateException("La sucursal ya está en el estado solicitado");
        }

        branchEntity.setActiveStatus(status);
        save(branchEntity);
    }

    private void validateBranchExists(BranchEntity branchEntity, ClubEntity clubEntity, String branchDtoName){
        if(branchRepository.existsByAddressAndClub(branchEntity.getAddress(), clubEntity)){
            throw new BranchAlreadyExistsException("Ya existe una sucursal en esta dirección");
        }

        if(branchRepository.existsByNameIgnoreCaseAndClub(branchDtoName, clubEntity)){
            throw new BranchAlreadyExistsException("Ya existe una sucursal con el nombre " + branchDtoName);
        }
    }
}
