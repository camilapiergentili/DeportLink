package com.deportlink.DeportLink.dto.request;

import com.deportlink.DeportLink.model.Rol;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
public class UserRequestDto {

    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String confirmPassword;
    private String phone;

}
