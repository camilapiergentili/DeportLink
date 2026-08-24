package com.deportlink.deportlink.dto.response;

import com.deportlink.deportlink.enums.ActiveStatus;
import com.deportlink.deportlink.enums.VerificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BranchResponseDto {

    private Long id;
    private String name;
    private Long clubId;
    private AddressResponseDto address;
    private VerificationStatus verificationStatus;
    private ActiveStatus activeStatus;
    private Integer cancellationWindowHours;
    // courts cargadas bajo demanda vía GET /api/courts/branch/{id}
    private List<CourtResponseDto> courts;
}
