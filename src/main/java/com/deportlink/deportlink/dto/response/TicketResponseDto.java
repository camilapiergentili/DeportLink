package com.deportlink.deportlink.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TicketResponseDto {

    private long id;
    private Double totalPrice;
    private String branchName;
    private String branchAddress;
    private String courtName;
    private String sport;
    private String player;
    private String issuedAt;

}
