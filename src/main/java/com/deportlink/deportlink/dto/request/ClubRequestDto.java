package com.deportlink.deportlink.dto.request;

import com.deportlink.deportlink.enums.ClubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class ClubRequestDto {

    @NotBlank
    private String name;

    @NotBlank
    private String legalName;

    @NotNull
    private ClubType clubType;

    @NotBlank
    private String cuit;

    @NotEmpty
    private Set<Long> ownerIds;
}