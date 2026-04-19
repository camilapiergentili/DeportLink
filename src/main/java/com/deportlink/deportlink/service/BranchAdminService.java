package com.deportlink.deportlink.service;

public interface BranchAdminService {
    void approve(long idBranch);
    void reject(long idBranch);
}
