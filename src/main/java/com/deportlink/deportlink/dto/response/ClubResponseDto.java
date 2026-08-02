package com.deportlink.deportlink.dto.response;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.ClubType;
import com.deportlink.deportlink.enums.VerificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
public class ClubResponseDto {

    private Long id;
    private String name;
    private String legalName;
    private String cuit;
    private ClubType clubType;
    private VerificationStatus verificationStatus;
    private ActiveStatus activeStatus;
    // branches se carga bajo demanda vía GET /api/branches/club/{id}
    // Se mantiene el campo para compatibilidad con ClubMapper (MapStruct) existente
    private Set<BranchResponseDto> branches;

}