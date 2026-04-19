package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.response.CourtResponseDto;

import java.util.List;

public interface CourtAdminService {

    List<CourtResponseDto> getAll();
}
