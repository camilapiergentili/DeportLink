package com.deportlink.DeportLink.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {

    private long id;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phone;
    private Rol role;
    private Set<Address> address;

    //Reservations of players
    private Set<Reservation> reservations;

    //Courts of owner
    private List<Court> courts;

}
