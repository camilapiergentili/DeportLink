package com.deportlink.deportlink.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Subtipo de UserEntity, mismo patrón exacto que OwnerEntity/PlayerEntity
 * (@Inheritance(JOINED) declarado en UserEntity, @PrimaryKeyJoinColumn acá).
 * Sin columnas propias — igual que PlayerEntity. El alta con credenciales
 * (RegisterInstructorUseCase) queda diferida; esta entidad solo existe para que
 * ClassSlot.instructor_id tenga a qué apuntar (ver docs/class-management-stage-1c-persistence-design.md).
 */
@Entity
@Table(name = "instructors")
@PrimaryKeyJoinColumn(name = "id")
@Getter
@Setter
@NoArgsConstructor
public class InstructorEntity extends UserEntity {
}
