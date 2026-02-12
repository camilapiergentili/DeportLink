package com.deportlink.deportlink.dto.response;

import com.deportlink.deportlink.model.ActiveStatus;
import com.deportlink.deportlink.model.VerificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BranchResponseDto {

    private long id;
    private String name;
    private List<CourtResponseDto> courts;
    private AddressResponseDto address;
    private VerificationStatus verificationStatus;
    private ActiveStatus activeStatus;
}
