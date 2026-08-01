package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.response.BranchResponseDto;
import com.deportlink.deportlink.dto.response.ClubResponseDto;
import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.mapper.BranchMapper;
import com.deportlink.deportlink.mapper.CourtMapper;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.ClubEntity;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.service.AdministratorService;
import com.deportlink.deportlink.service.ClubService;
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

    private final ClubService clubService;
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
        clubService.approve(idClub);
        log.info("Club approved successfully: clubId={}", idClub);
    }

    public void rejectClub(long idClub){
        log.info("Rejecting club: clubId={}", idClub);
        clubService.reject(idClub);
        log.info("Club rejected successfully: clubId={}", idClub);
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
        branchService.approve(idBranch);
        log.info("Branch approved successfully: branchId={}", idBranch);
    }

    public void rejectBranch(long idBranch){
        log.info("Rejecting branch: branchId={}", idBranch);
        branchService.reject(idBranch);
        log.info("Branch rejected successfully: branchId={}", idBranch);
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