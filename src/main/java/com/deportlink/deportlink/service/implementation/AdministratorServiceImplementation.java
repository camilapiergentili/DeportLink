package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.StatusAlreadyExistsException;
import com.deportlink.deportlink.mapper.ClubMapper;
import com.deportlink.deportlink.mapper.CourtMapper;
import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.VerificationStatus;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.service.AdministratorService;
import com.deportlink.deportlink.service.BranchService;
import com.deportlink.deportlink.service.CourtService;
import com.deportlink.deportlink.service.implementation.court.CourtServiceImplementation;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AdministratorServiceImplementation implements AdministratorService {

    private final ClubServiceImplementation clubService;
    private final CourtServiceImplementation courtService;
    private final BranchServiceImplementation branchService;
    private final CourtMapper courtMapper;
    private final ClubMapper clubMapper;

    // CLUB
    public List<ClubResponseDto> getAllClubs(){
        return clubService.getAll();
    }

    public ClubResponseDto getClubById(long idClub){
        ClubEntity clubEntity = clubService.getById(idClub);
        return clubMapper.toResponse(clubEntity);
    }

    public void approveClub(long idClub){
        modifyStatusClub(idClub, ActiveStatus.ACTIVE, VerificationStatus.APPROVED);
    }

    public void rejectClub(long idClub){
        modifyStatusClub(idClub, ActiveStatus.DESACTIVE, VerificationStatus.REJECTED);
    }

    private void modifyStatusClub(long idClub, ActiveStatus activeStatus, VerificationStatus verificationStatus){
        ClubEntity clubEntity = clubService.getById(idClub);

        if(!clubEntity.getVerificationStatus().equals(VerificationStatus.PENDING)){
            throw new IllegalStateException("Solo se pueden aprobar/rechazar clubs en estado PENDING");
        }

        if(clubEntity.getActiveStatus().equals(activeStatus) &&
                clubEntity.getVerificationStatus().equals(verificationStatus)){
            throw new StatusAlreadyExistsException("El club ya se encuentra " + activeStatus + " y " + verificationStatus);
        }

        clubEntity.setActiveStatus(activeStatus);
        clubEntity.setVerificationStatus(verificationStatus);
        clubService.save(clubEntity);
    }

    //BRANCH




    //COURT
    public CourtResponseDto getCourtById(long idCourt){
        CourtEntity courtEntity = courtService.getById(idCourt);
        return courtMapper.toResponse(courtEntity);
    }

    public List<CourtResponseDto> getAllResponse(){
        return courtService.getAll();
    }

    public List<CourtResponseDto> getAllByBranch(long idBranch){
        BranchEntity branchEntity = branchService.getById(idBranch);

        List<CourtEntity> courts = branchEntity.getCourts();

        if(courts.isEmpty()){
            throw new CourtNotFoundException("No se encontraron canchas asociadas a la sucursal");
        }

        return courts.stream()
                .map(courtMapper::toResponse)
                .collect(Collectors.toList());
    }
}
