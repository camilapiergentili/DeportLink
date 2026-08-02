package com.deportlink.deportlink.application.port.out;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gateway hacia el dominio Schedule, definido por el caso de uso de reservas.
 * Expone la configuración de slots que Reservation necesita para validar y generar turnos.
 */
public interface ScheduleGateway {

    Optional<SlotConfig> findByCourtAndDay(Long courtId, DayOfWeek day);

    /**
     * Configuración de horarios de una cancha para un día dado.
     * Concentra la lógica de generación y validación de slots — antes estaba duplicada
     * en AppointmentServiceImplementation y ScheduleServiceImplementation.
     */
    record SlotConfig(LocalTime openingTime, LocalTime closingTime, Duration slotDuration) {

        public List<LocalTime> generateSlots() {
            List<LocalTime> slots = new ArrayList<>();
            LocalTime current = openingTime;
            long minutes = slotDuration.toMinutes();
            while (!current.plusMinutes(minutes).isAfter(closingTime)) {
                slots.add(current);
                current = current.plusMinutes(minutes);
            }
            return slots;
        }

        public boolean isValidSlot(LocalTime time) {
            LocalTime current = openingTime;
            long minutes = slotDuration.toMinutes();
            while (!current.plusMinutes(minutes).isAfter(closingTime)) {
                if (current.equals(time)) return true;
                current = current.plusMinutes(minutes);
            }
            return false;
        }
    }
}