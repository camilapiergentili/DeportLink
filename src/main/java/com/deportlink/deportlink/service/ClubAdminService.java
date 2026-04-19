package com.deportlink.deportlink.service;

public interface ClubAdminService {

    void approve(long idClub);
    void reject(long idClub);
}
