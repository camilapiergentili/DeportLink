package com.deportlink.deportlink.utils;

import com.deportlink.deportlink.exception.UnderageException;

import java.time.LocalDate;
import java.time.Period;

public class DateUtils {

    public static boolean isOfLegalAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            throw new UnderageException("La fecha no puede ser null");
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears() >= 18;
    }
}
