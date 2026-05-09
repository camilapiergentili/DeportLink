package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.SportRequestDto;
import com.deportlink.deportlink.dto.response.SportResponseDto;

import java.util.List;

public interface SportService {
    SportResponseDto create(SportRequestDto sportDto);
    SportResponseDto getByIdResponse(long id);
    void delete(long id);
    List<SportResponseDto> getAll();
}
