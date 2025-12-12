package com.deportlink.DeportLink.service.implementation.court;

import com.deportlink.DeportLink.dto.request.CourtRequestDto;
import com.deportlink.DeportLink.dto.response.CourtResponseDto;
import com.deportlink.DeportLink.exception.BranchNotApprovedException;
import com.deportlink.DeportLink.exception.CourtAlreadyExistsException;
import com.deportlink.DeportLink.exception.CourtNotFoundException;
import com.deportlink.DeportLink.exception.NegativePriceExcepcion;
import com.deportlink.DeportLink.mapper.BranchMapper;
import com.deportlink.DeportLink.mapper.CourtMapper;
import com.deportlink.DeportLink.model.ActiveStatus;
import com.deportlink.DeportLink.model.VerificationStatus;
import com.deportlink.DeportLink.model.entity.BranchEntity;
import com.deportlink.DeportLink.model.entity.CourtEntity;
import com.deportlink.DeportLink.model.entity.SportEntity;
import com.deportlink.DeportLink.persistence.repository.CourtRepository;
import com.deportlink.DeportLink.service.CourtService;
import com.deportlink.DeportLink.service.implementation.BranchServiceImplementation;
import com.deportlink.DeportLink.service.implementation.SportServiceImplementation;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class CourtServiceImplementation implements CourtService {

    private final CourtRepository courtRepository;
    private final CourtMapper courtMapper;
    private final BranchServiceImplementation branchService;
    private final SportServiceImplementation sportService;

    public void create(CourtRequestDto courtDto) {

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
    }

    public CourtEntity getById(long idCourt){
        return courtRepository.findById(idCourt)
                .orElseThrow(() -> new CourtNotFoundException("No se encontro la cancha"));
    }

    public CourtResponseDto getByIdApprovedAndActive(long idCourt){
        CourtEntity courtEntity = getById(idCourt);

        boolean isVisible = verifyBranchIsVisibleForPlayer(courtEntity.getBranch().getVerificationStatus(), courtEntity.getBranch().getActiveStatus());

        if(!isVisible){
            throw new BranchNotApprovedException("La cancha no está disponible para jugadores");
        }

        return courtMapper.toResponse(courtEntity);
    }

    public List<CourtResponseDto> getAllBranch(long idBranch){
        BranchEntity branchEntity = branchService.getById(idBranch);

        return branchEntity.getCourts().stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());

    }

    public List<CourtResponseDto> getAll(){
        List<CourtEntity> courtEntities = courtRepository.findAll();

        return courtEntities.stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());
    }


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

    public List<CourtResponseDto> getCourtsByBranchAndSport(long idBranch, long idSport){
        List<CourtEntity> courtEntityList = courtRepository.findByBranch_IdAndSport_Id(idBranch, idSport);

        if(courtEntityList.isEmpty()){
            throw new CourtNotFoundException("No se encontraron datos con los filros seleccionados");
        }

        return courtEntityList.stream().filter(c -> verifyBranchIsVisibleForPlayer(c.getBranch().getVerificationStatus(), c.getActiveStatus()))
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());
    }

    public CourtEntity getCourtByIdWithSchedule(long idCourt) {
        return courtRepository.findByCourtWithSchedule(idCourt)
                .orElseThrow(() -> new CourtNotFoundException("La cancha que esta buscando no se encuentra registrada"));
    }

    public void delete(long idCourt){
        CourtEntity courtToDelete = getById(idCourt);
        courtRepository.delete(courtToDelete);
    }

    public CourtResponseDto update(long idCourt, CourtRequestDto courtDto) {
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

        courtRepository.save(courtEntity);

        return courtMapper.toResponse(courtEntity);
    }

    public void updatePrice(long idCourt, double newPrice){
        CourtEntity courtEntity = getById(idCourt);

        if(newPrice < 0){
            throw new NegativePriceExcepcion("El precio no pueder ser negativo");
        }

        courtEntity.setPricePerHour(newPrice);
        courtRepository.save(courtEntity);
    }

    public void activateCourt(long idBranch, long idCourt){
        ActiveStatus status = ActiveStatus.ACTIVE;
        activateAndDesactivateCourtByBranch(idCourt, idBranch, status);
    }

    public void desactivedCourt(long idBranch, long idCourt){
        ActiveStatus status = ActiveStatus.DESACTIVE;
        activateAndDesactivateCourtByBranch(idCourt, idBranch, status);
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
