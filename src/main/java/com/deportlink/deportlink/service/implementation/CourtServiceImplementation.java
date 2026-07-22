package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.request.CourtRequestDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.exception.BranchNotApprovedException;
import com.deportlink.deportlink.exception.CourtAlreadyExistsException;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.NegativePriceException;
import com.deportlink.deportlink.mapper.CourtMapper;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.model.entity.SportEntity;
import com.deportlink.deportlink.persistence.repository.CourtRepository;
import com.deportlink.deportlink.service.CourtAdminService;
import com.deportlink.deportlink.service.CourtOwnerService;
import com.deportlink.deportlink.service.CourtService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class CourtServiceImplementation implements CourtService, CourtOwnerService, CourtAdminService {

    private final CourtRepository courtRepository;
    private final CourtMapper courtMapper;
    private final BranchServiceImplementation branchService;
    private final SportServiceImplementation sportService;

    @Override
    @Transactional
    public CourtResponseDto create(CourtRequestDto courtDto) {
        log.info("Creating court: name={}, branchId={}, sportId={}", courtDto.getName(), courtDto.getIdBranch(), courtDto.getIdSport());

        BranchEntity branchEntity = branchService.getById(courtDto.getIdBranch());
        SportEntity sportEntity = sportService.getById(courtDto.getIdSport());

        if(!branchEntity.getVerificationStatus().equals(VerificationStatus.APPROVED)
                || !branchEntity.getActiveStatus().equals(ActiveStatus.ACTIVE)){
            throw new BranchNotApprovedException("La sucursal no puede agregar canchas");
        }

        validateUniqueCourt(courtDto.getName(), branchEntity.getId(), sportEntity.getId());

        CourtEntity courtEntity = courtMapper.toModel(courtDto);
        courtEntity.setSport(sportEntity);
        courtEntity.setBranch(branchEntity);
        courtEntity.setActiveStatus(ActiveStatus.ACTIVE);

        courtRepository.save(courtEntity);
        log.info("Court created successfully: courtId={}", courtEntity.getId());

        return courtMapper.toResponse(courtEntity);
    }

    @Transactional(readOnly = true)
    public CourtEntity getById(long idCourt){
        return courtRepository.findById(idCourt)
                .orElseThrow(() -> new CourtNotFoundException("No se encontro la cancha"));
    }

    @Override
    @Transactional(readOnly = true)
    public CourtResponseDto getByIdResponse(long idCourt){
        CourtEntity courtEntity = getById(idCourt);
        return courtMapper.toResponse(courtEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public CourtResponseDto getByIdApprovedAndActive(long idCourt){
        CourtEntity courtEntity = getById(idCourt);

        boolean isVisible = verifyBranchIsVisibleForPlayer(courtEntity.getBranch().getVerificationStatus(), courtEntity.getBranch().getActiveStatus());

        if(!isVisible){
            throw new BranchNotApprovedException("La cancha no está disponible para jugadores");
        }

        return courtMapper.toResponse(courtEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourtResponseDto> getAllBranch(long idBranch){
        log.debug("Fetching courts for branch: branchId={}", idBranch);
        return courtRepository.findByBranchIdWithEagerLoading(idBranch)
                .stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourtResponseDto> getAll(){
        List<CourtEntity> courtEntities = courtRepository.findAll();

        return courtEntities.stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());
    }


    @Override
    @Transactional(readOnly = true)
    public List<CourtResponseDto> getAllActiveAndApproved(){

        List<CourtEntity> courtEntities = courtRepository
                .findByBranch_VerificationStatusAndBranch_ActiveStatus(
                        VerificationStatus.APPROVED,
                        ActiveStatus.ACTIVE
                );

        return courtEntities.stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourtResponseDto> getAllActiveAndApprovedPaginated(Pageable pageable){
        log.info("Fetching active and approved courts with pagination: page={}, size={}", pageable.getPageNumber(), pageable.getPageSize());

        Page<CourtEntity> courtEntities = courtRepository
                .findByBranch_VerificationStatusAndBranch_ActiveStatus(
                        VerificationStatus.APPROVED,
                        ActiveStatus.ACTIVE,
                        pageable
                );

        log.info("Found {} courts for pagination", courtEntities.getTotalElements());
        return courtEntities.map(courtMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourtResponseDto> getAllByBranchActiveAndApproved(long idBranch){
        BranchEntity branchEntity = branchService.getById(idBranch);

        boolean isVisible = verifyBranchIsVisibleForPlayer(branchEntity.getVerificationStatus(), branchEntity.getActiveStatus());

        if(!isVisible){
            throw new BranchNotApprovedException("La cancha no está disponible para jugadores");
        }

        List<CourtEntity> courts = branchEntity.getCourts();

        if(courts.isEmpty()){
            throw new CourtNotFoundException("No se encontraron canchas asociadas a la sucursal");
        }

        return courts.stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CourtResponseDto> getAllByBranchActiveAndApprovedPaginated(long idBranch, Pageable pageable){
        log.info("Fetching active and approved courts for branch with pagination: branchId={}, page={}, size={}", idBranch, pageable.getPageNumber(), pageable.getPageSize());
        
        BranchEntity branchEntity = branchService.getById(idBranch);

        boolean isVisible = verifyBranchIsVisibleForPlayer(branchEntity.getVerificationStatus(), branchEntity.getActiveStatus());

        if(!isVisible){
            throw new BranchNotApprovedException("La cancha no está disponible para jugadores");
        }

        List<CourtEntity> courts = branchEntity.getCourts();

        if(courts.isEmpty()){
            throw new CourtNotFoundException("No se encontraron canchas asociadas a la sucursal");
        }

        log.info("Found {} courts for branch with pagination", courts.size());
        
        // Manual pagination of in-memory list
        int pageNumber = pageable.getPageNumber();
        int pageSize = pageable.getPageSize();
        int start = pageNumber * pageSize;
        int end = Math.min(start + pageSize, courts.size());
        
        List<CourtResponseDto> pageContent = courts.subList(start, Math.max(start, end))
                .stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());
        
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, courts.size());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourtResponseDto> getCourtsByBranchAndSport(long idBranch, long idSport){
        List<CourtEntity> courtEntityList = courtRepository.findByBranch_IdAndSport_Id(idBranch, idSport);

        if(courtEntityList.isEmpty()){
            throw new CourtNotFoundException("No se encontraron datos con los filros seleccionados");
        }

        List<CourtResponseDto> result = courtEntityList.stream()
                .filter(c -> verifyBranchIsVisibleForPlayer(c.getBranch().getVerificationStatus(),
                        c.getBranch().getActiveStatus()))
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());

        if(result.isEmpty()){
            throw new BranchNotApprovedException("La sucursal no está disponible");
        }

        return result;
    }

    @Override
    @Transactional
    public void delete(long idCourt){
        log.info("Deleting court: courtId={}", idCourt);

        CourtEntity courtToDelete = getById(idCourt);
        courtRepository.delete(courtToDelete);
        log.info("Court deleted successfully: courtId={}", idCourt);
    }

    @Override
    @Transactional
    public CourtResponseDto update(long idCourt, CourtRequestDto courtDto) {
        log.info("Updating court: courtId={}, name={}, branchId={}", idCourt, courtDto.getName(), courtDto.getIdBranch());

        CourtEntity courtEntity = getById(idCourt);
        BranchEntity branchEntity = branchService.getById(courtDto.getIdBranch());
        SportEntity sportEntity = sportService.getById(courtDto.getIdSport());

        boolean sameSport = courtEntity.getSport().getId() == sportEntity.getId();
        boolean sameName = courtEntity.getName().equalsIgnoreCase(courtDto.getName());

        if(!sameSport || !sameName){
            validateUniqueCourt(courtDto.getName(), branchEntity.getId(), sportEntity.getId());
        }

        courtEntity.setName(courtDto.getName());
        courtEntity.setSport(sportEntity);
        courtEntity.setBranch(branchEntity);

        courtRepository.save(courtEntity);
        log.info("Court updated successfully: courtId={}", courtEntity.getId());

        return courtMapper.toResponse(courtEntity);
    }

    @Override
    @Transactional
    public void updatePrice(long idCourt, double newPrice) {
        log.info("Updating price for court: courtId={}, newPrice={}", idCourt, newPrice);

        CourtEntity courtEntity = getById(idCourt);

        if(newPrice <= 0){
            throw new NegativePriceException("El precio no pueder ser negativo o cero");
        }

        courtEntity.setPricePerHour(newPrice);
        courtRepository.save(courtEntity);
        log.info("Court price updated successfully: courtId={}", idCourt);
    }

    @Override
    @Transactional
    public void activateCourt(long idBranch, long idCourt){
        log.info("Activating court: courtId={}, branchId={}", idCourt, idBranch);
        ActiveStatus status = ActiveStatus.ACTIVE;
        activateAndDesactivateCourtByBranch(idCourt, idBranch, status);
        log.info("Court activated successfully: courtId={}", idCourt);
    }

    @Override
    @Transactional
    public void desactivedCourt(long idBranch, long idCourt){
        log.info("Deactivating court: courtId={}, branchId={}", idCourt, idBranch);

        ActiveStatus status = ActiveStatus.DESACTIVE;
        activateAndDesactivateCourtByBranch(idCourt, idBranch, status);

        log.info("Court deactivated successfully: courtId={}", idCourt);
    }

    @Transactional(readOnly = true)
    public CourtEntity getCourtByIdWithSchedule(long idCourt) {
        return courtRepository.findByCourtWithSchedule(idCourt)
                .orElseThrow(() -> new CourtNotFoundException("La cancha que esta buscando no se encuentra registrada"));
    }

    private void activateAndDesactivateCourtByBranch(long idCourt, long idBranch, ActiveStatus status){
        CourtEntity courtEntity = courtRepository.findByIdAndBranch_Id(idCourt, idBranch)
                .orElseThrow(() -> new CourtNotFoundException("No se encontre cancha vinculada a la sucursal"));

        if(!courtEntity.getBranch().getVerificationStatus().equals(VerificationStatus.APPROVED)){
            throw new BranchNotApprovedException(
                    "La sucursal no se encuentra APROBADA para poder usar la función de activar y desactivar cancha");
        }

        if(courtEntity.getBranch().getActiveStatus().equals(ActiveStatus.DESACTIVE)){
            throw new BranchNotApprovedException(
                    "La sucursal no se encuentra ACTIVA para poder usar la función de activar y desactivar cancha");
        }

        if(courtEntity.getActiveStatus().equals(status)){
            throw new IllegalStateException("La cancha se encuentra en el estado solicitado");
        }

        courtEntity.setActiveStatus(status);
        courtRepository.save(courtEntity);
    }

    private void validateUniqueCourt(String nameCourt, long branchId, long sportId){
        if(courtRepository.findByNameAndBranchIdAndSportId(
                nameCourt,
                branchId,
                sportId
        ).isPresent()){
            throw new CourtAlreadyExistsException("El nombre de la cancha "
                    + nameCourt + " ya se encuentra en esta sucursal");
        }
    }

    private boolean verifyBranchIsVisibleForPlayer(VerificationStatus verificationStatus, ActiveStatus activeStatus){
        return verificationStatus.equals(VerificationStatus.APPROVED)
                && activeStatus.equals(ActiveStatus.ACTIVE);

    }

}
