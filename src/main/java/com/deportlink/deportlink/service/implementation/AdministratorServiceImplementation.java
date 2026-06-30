package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.response.BranchResponseDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.exception.StatusAlreadyExistsException;
import com.deportlink.deportlink.mapper.BranchMapper;
import com.deportlink.deportlink.mapper.CourtMapper;
import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.service.AdministratorService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class AdministratorServiceImplementation implements AdministratorService {

    private final ClubServiceImplementation clubService;
    private final CourtServiceImplementation courtService;
    private final BranchServiceImplementation branchService;
    private final CourtMapper courtMapper;
    private final BranchMapper branchMapper;

    // CLUB
    public List<ClubResponseDto> getAllClubs(){
        return clubService.getAll();
    }

    public ClubResponseDto getClubById(long idClub){
        return clubService.getByIdResponse(idClub);
    }

    public void approveClub(long idClub){
        log.info("Approving club: clubId={}", idClub);

        try {
            clubService.approve(idClub);
            log.info("Club approved successfully: clubId={}", idClub);
        } catch (Exception e) {
            log.error("Failed to approve club {}: {}", idClub, e.getMessage(), e);
            throw e;
        }
    }

    public void rejectClub(long idClub){
        log.info("Rejecting club: clubId={}", idClub);

        try {
            clubService.reject(idClub);
            log.info("Club rejected successfully: clubId={}", idClub);
        } catch (Exception e) {
            log.error("Failed to reject club {}: {}", idClub, e.getMessage(), e);
            throw e;
        }
    }



    //BRANCH

    public BranchResponseDto getBranchById(long idBranch){
        BranchEntity branchEntity = branchService.getById(idBranch);
        return branchMapper.toResponse(branchEntity);
    }

    public List<BranchResponseDto> getAllByClub(long idClub){
        ClubEntity clubEntity = clubService.getById(idClub);
        List<BranchEntity> branchesByClub = new ArrayList<>(clubEntity.getBranches());
        return branchesByClub.stream().map(branchMapper::toResponse)
                .toList();

    }

    public void approveBranch(long idBranch){
        log.info("Approving branch: branchId={}", idBranch);

        try {
            modifyStatusBranch(idBranch, ActiveStatus.ACTIVE, VerificationStatus.APPROVED);
            log.info("Branch approved successfully: branchId={}", idBranch);
        } catch (Exception e) {
            log.error("Failed to approve branch {}: {}", idBranch, e.getMessage(), e);
            throw e;
        }
    }

    public void rejectBranch(long idBranch){
        log.info("Rejecting branch: branchId={}", idBranch);

        try {
            modifyStatusBranch(idBranch, ActiveStatus.DESACTIVE, VerificationStatus.REJECTED);
            log.info("Branch rejected successfully: branchId={}", idBranch);
        } catch (Exception e) {
            log.error("Failed to reject branch {}: {}", idBranch, e.getMessage(), e);
            throw e;
        }
    }

    private void modifyStatusBranch(long idBranch, ActiveStatus activeStatus, VerificationStatus verificationStatus){
        BranchEntity branchEntity = branchService.getById(idBranch);

        if(!branchEntity.getVerificationStatus().equals(VerificationStatus.PENDING)){
            throw new IllegalStateException("Solo se pueden aprobar/rechazar sucursales en estado PENDING");
        }

        if(branchEntity.getActiveStatus().equals(activeStatus) &&
                branchEntity.getVerificationStatus().equals(verificationStatus)){
            throw new StatusAlreadyExistsException("La sucursal ya se encuentra " + activeStatus + " y " + verificationStatus);
        }

        branchEntity.setActiveStatus(activeStatus);
        branchEntity.setVerificationStatus(verificationStatus);
        branchService.save(branchEntity);
    }

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
