package com.deportlink.DeportLink.dto.response;

import com.deportlink.DeportLink.model.Rol;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserResponseDto {

    private long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Rol role;

}
