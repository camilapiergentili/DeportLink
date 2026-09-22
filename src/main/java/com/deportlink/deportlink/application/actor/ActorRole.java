package com.deportlink.deportlink.application.actor;

/**
 * Rol del actor que ejecuta un caso de uso del módulo de clases — deliberadamente separado del
 * enum {@code model.Rol} (OWNER/PLAYER/ADMIN) usado por la autenticación real: agregar
 * INSTRUCTOR ahí requiere tocar UserEntity/JWT/SecurityConfig, y esa integración queda diferida
 * (ver docs/class-management-mvp-design.md — RegisterInstructorUseCase diferido). Cuando esa
 * etapa llegue, el controller/adapter que construya {@link Actor} deberá mapear
 * {@code model.Rol.INSTRUCTOR} a este valor — el dominio y los casos de uso de este módulo no
 * cambian.
 * <p>
 * PLAYER no está representado a propósito: ningún caso de uso de esta etapa lo ejecuta.
 */
public enum ActorRole {
    INSTRUCTOR,
    ADMIN
}
