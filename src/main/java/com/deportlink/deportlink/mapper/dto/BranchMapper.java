package com.deportlink.deportlink.mapper.dto;

import com.deportlink.deportlink.domain.model.Branch;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR, uses = {AddressMapper.class})
public interface BranchMapper {

    // courts se carga bajo demanda — no existe en el agregado Branch
    @Mapping(target = "courts", ignore = true)
    BranchResponseDto toResponse(Branch branch);
}