package com.deportlink.deportlink;

import com.deportlink.deportlink.exception.UnderageException;
import com.deportlink.deportlink.utils.DateUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;


public class OwnerServiceTest {

    @Test
    void isOfLegalAge_mayor18(){
        LocalDate fecha = LocalDate.of(1997, 5, 11);

        boolean result = DateUtils.isOfLegalAge(fecha);

        assertTrue(result);
    }

    @Test
    void isOfLegalAge_menor18(){
        LocalDate fecha = LocalDate.of(2016, 5, 11);

        boolean result = DateUtils.isOfLegalAge(fecha);

        assertFalse(result);
    }

    @Test
    void isOfLegalAge_edadFutura(){
        LocalDate fecha = LocalDate.of(2045, 5, 11);

        boolean result = DateUtils.isOfLegalAge(fecha);

        assertFalse(result);
    }

    @Test
    void isOfLegalAge_ageNUll_debeLanzarException(){
        assertThrows(UnderageException.class, () -> {
            DateUtils.isOfLegalAge(null);
        });
    }

}
