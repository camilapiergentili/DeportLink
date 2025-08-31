package com.deportlink.DeportLink.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Sport {

    private long id;
    private String nameSport;
    private Set<Court> courts;
}
