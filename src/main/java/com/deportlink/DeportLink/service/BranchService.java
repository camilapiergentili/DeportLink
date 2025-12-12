package com.deportlink.DeportLink.service;

import com.deportlink.DeportLink.dto.request.BranchRequestDto;
import com.deportlink.DeportLink.dto.response.BranchResponseDto;
import com.deportlink.DeportLink.model.entity.BranchEntity;

import java.util.List;

public interface BranchService {
    void create(BranchRequestDto branchDto);
    BranchEntity getById(long id);
    List<BranchResponseDto> getAllActiveAndApproved(long idClub);
    List<BranchResponseDto> getAll(long idClub);
    BranchResponseDto getByIdResponse(long id);
    BranchResponseDto getByIdApproved(long id);
    void deleteById(long id);
    void update(long id, BranchRequestDto branchDto);
    void desactivedBranch(long idBranch, long idClub);
    void activedBranch(long idBranch, long idClub);
}
