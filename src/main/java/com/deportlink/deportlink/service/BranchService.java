package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.BranchRequestDto;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import com.deportlink.deportlink.model.entity.BranchEntity;

import java.util.List;

public interface BranchService {

    BranchEntity getById(long id);
    List<BranchResponseDto> getAllActiveAndApproved(long idClub);
    List<BranchResponseDto> getAll(long idClub);
    BranchResponseDto getByIdResponse(long id);
    BranchResponseDto getByIdApproved(long id);

}
