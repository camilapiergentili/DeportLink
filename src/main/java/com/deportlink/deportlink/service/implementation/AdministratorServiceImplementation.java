package com.deportlink.deportlink.service.implementation;

import com.deportlink.deportlink.dto.response.CourtResponseDto;
import com.deportlink.deportlink.exception.CourtNotFoundException;
import com.deportlink.deportlink.mapper.CourtMapper;
import com.deportlink.deportlink.model.entity.BranchEntity;
import com.deportlink.deportlink.model.entity.CourtEntity;
import com.deportlink.deportlink.service.AdministratorService;
import com.deportlink.deportlink.service.BranchService;
import com.deportlink.deportlink.service.CourtService;
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
