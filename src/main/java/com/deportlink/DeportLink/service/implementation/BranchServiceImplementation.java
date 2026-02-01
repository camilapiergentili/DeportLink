package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.request.BranchRequestDto;
import com.deportlink.DeportLink.dto.response.BranchResponseDto;
import com.deportlink.DeportLink.exception.*;
import com.deportlink.DeportLink.mapper.AddressMapper;
import com.deportlink.DeportLink.mapper.BranchMapper;
import com.deportlink.DeportLink.model.ActiveStatus;
import com.deportlink.DeportLink.model.VerificationStatus;
import com.deportlink.DeportLink.model.entity.BranchEntity;
import com.deportlink.DeportLink.model.entity.ClubEntity;
import com.deportlink.DeportLink.model.entity.OwnerEntity;
import com.deportlink.DeportLink.persistence.repository.BranchRepository;
import com.deportlink.DeportLink.service.BranchService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class BranchServiceImplementation implements BranchService {

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;
    private final AddressMapper addressMapper;
    private final ClubServiceImplementation clubService;

    public void create(BranchRequestDto branchDto){

        ClubEntity clubEntity = clubService.getById(branchDto.getIdClub());
        BranchEntity branchEntity = branchMapper.toModel(branchDto);

        if(clubEntity.getVerificationStatus() != VerificationStatus.APPROVED){
            throw new ClubNotApprovedException("El club " + clubEntity.getLegalName() + " aun no esta autorizado para agregar sucursales");
        }

        boolean existsAddress = branchEntity.getAddress()
                .equals(addressMapper.toModel(branchDto.getAddressRequestDto()));

        if(existsAddress){
            throw new BranchAlreadyExistsException("Ya existe una sucursal en esta dirección");
        }

        boolean existsName = branchEntity.getName().equalsIgnoreCase(branchDto.getName());

        if(existsName){
            throw new BranchAlreadyExistsException("Ya existe una sucursal con el nombre " + branchDto.getName());
        }

        branchEntity.setClub(clubEntity);
        clubEntity.getBranches().add(branchEntity);

        branchEntity.setVerificationStatus(VerificationStatus.PENDING);

        branchRepository.save(branchEntity);
    }

    public BranchEntity getById(long id){
        return branchRepository.findById(id)
                .orElseThrow(() -> new BranchNotFoundException("No se encontro registro de la cancha"));
    }

    public List<BranchResponseDto> getAllActiveAndApproved(long idClub){

        List<BranchEntity> branchesEntity = branchRepository.findAllByClubId(idClub);

        if(branchesEntity.isEmpty()){
            throw new BranchNotFoundException("El club aun no tiene sucursales registradas");
        }

        return branchesEntity.stream()
                .filter(b -> b.getActiveStatus() == ActiveStatus.ACTIVE
                        && b.getVerificationStatus() == VerificationStatus.APPROVED)
                .map(branchMapper::toResponse)
                .collect(Collectors.toList());

    }

    public List<BranchResponseDto> getAll(long idClub){

        clubService.getById(idClub);
        List<BranchEntity> branchesEntity = branchRepository.findAllByClubId(idClub);

        return branchesEntity.stream()
                .map(branchMapper::toResponse)
                .collect(Collectors.toList());
    }

    public BranchResponseDto getByIdResponse(long id){
        BranchEntity branchEntity = getById(id);

        return branchMapper.toResponse(branchEntity);
    }

    public BranchResponseDto getByIdApproved(long id){
        BranchEntity branchEntity = getById(id);

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

    public void deleteById(long id){
        BranchEntity branchEntity = getById(id);

        branchRepository.delete(branchEntity);
    }

    @Transactional
    public void update(long id, BranchRequestDto branchDto){
        BranchEntity branchEntity = getById(id);

        BranchEntity branchDtoToModel = branchMapper.toModel(branchDto);

        boolean requiresReview = !branchEntity.getAddress().equals(branchDtoToModel.getAddress());

        branchEntity.setName(branchDtoToModel.getName());
        branchEntity.setAddress(branchDtoToModel.getAddress());

        if(requiresReview){
            branchEntity.setVerificationStatus(VerificationStatus.PENDING);
            branchEntity.setActiveStatus(ActiveStatus.DESACTIVE);
        }

        branchRepository.save(branchEntity);
    }

    public void desactivedBranch(long idBranch, long idClub){
        activateAndDesactivateBranchByClub(idBranch, idClub, ActiveStatus.DESACTIVE);
    }

    public void activedBranch(long idBranch, long idClub){
        activateAndDesactivateBranchByClub(idBranch, idClub, ActiveStatus.ACTIVE);
    }

    @Transactional
    private void activateAndDesactivateBranchByClub(long idClub, long idBranch, ActiveStatus status){
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
        branchRepository.save(branchEntity);
    }
}
