package com.deportlink.deportlink.security.resolver;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resuelve directamente al id del usuario autenticado, sin exponer
 * al controller el tipo de infraestructura que representa a ese usuario.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}
