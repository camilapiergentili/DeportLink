package com.deportlink.deportlink.service;

import com.deportlink.deportlink.dto.request.PlayerRequestDto;
import com.deportlink.deportlink.dto.response.PlayerResponseDto;
import com.deportlink.deportlink.model.entity.PlayerEntity;

public interface PlayerService {
    PlayerResponseDto register(PlayerRequestDto playerDto);
    PlayerEntity getById(long idPlayer);
    PlayerResponseDto getByIdResponse(long idPlayer);
    PlayerResponseDto update(long idPlayer, PlayerRequestDto playerDto);
    void delete(long idPlayer);
}
