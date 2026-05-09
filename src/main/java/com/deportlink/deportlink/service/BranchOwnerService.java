package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.BranchRequestDto;
import com.deportlink.deportlink.model.entity.BranchEntity;

public interface BranchOwnerService {
    void create(BranchRequestDto branchDto);
    void delete(long id);
    void update(long id, BranchRequestDto branchDto);
    void desactive(long idBranch, long idClub);
    void active(long idBranch, long idClub);
}
