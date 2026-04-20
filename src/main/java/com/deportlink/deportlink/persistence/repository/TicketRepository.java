package com.deportlink.deportlink.persistence.repository;

import com.deportlink.deportlink.model.entity.TicketEntity;
import org.springframework.data.jpa.repository.JpaRepository;


public interface TicketRepository extends JpaRepository<TicketEntity, Long> {
}
