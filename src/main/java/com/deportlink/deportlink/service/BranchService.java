package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.BranchRequestDto;
import com.deportlink.deportlink.dto.response.BranchResponseDto;
import com.deportlink.deportlink.model.entity.BranchEntity;

import java.util.List;

public interface BranchService {

    List<BranchResponseDto> getAllActiveAndApproved(long idClub);
    BranchResponseDto getByIdApproved(long id);
}
