package com.deportlink.DeportLink.mapper;

import com.deportlink.DeportLink.dto.request.BranchRequestDto;
import com.deportlink.DeportLink.dto.response.BranchResponseDto;
import com.deportlink.DeportLink.model.entity.BranchEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {CourtMapper.class, AddressMapper.class})
public interface BranchMapper {

    BranchEntity toModel(BranchRequestDto dto);

    BranchResponseDto toResponse(BranchEntity entity);
}
