package com.deportlink.deportlink.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MoveCourtBranchRequestDto {

    @NotNull(message = "El ID de la sucursal destino es obligatorio")
    private Long newBranchId;
}
