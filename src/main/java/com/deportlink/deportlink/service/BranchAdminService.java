package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.response.BranchResponseDto;

import java.util.List;

public interface BranchAdminService {
    void approve(long idBranch);
    void reject(long idBranch);
    List<BranchResponseDto> getAll(long idClub);
    BranchResponseDto getByIdResponse(long id);
}
