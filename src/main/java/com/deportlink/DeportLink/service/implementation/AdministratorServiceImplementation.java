package com.deportlink.DeportLink.service.implementation;

import com.deportlink.DeportLink.dto.response.CourtResponseDto;
import com.deportlink.DeportLink.exception.CourtNotFoundException;
import com.deportlink.DeportLink.mapper.CourtMapper;
import com.deportlink.DeportLink.model.entity.BranchEntity;
import com.deportlink.DeportLink.model.entity.CourtEntity;
import com.deportlink.DeportLink.service.AdministratorService;
import com.deportlink.DeportLink.service.BranchService;
import com.deportlink.DeportLink.service.CourtService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class AdministratorServiceImplementation implements AdministratorService {

    private CourtService courtService;
    private BranchService branchService;
    private CourtMapper courtMapper;

    public CourtResponseDto getCourtById(long idCourt){
        CourtEntity courtEntity = courtService.getById(idCourt);
        return courtMapper.toResponse(courtEntity);
    }

    public List<CourtResponseDto> getAllResponseForAdmin(){
        return courtService.getAll();
    }

    public List<CourtResponseDto> getAllByBranchForAdmin(long idBranch){
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
