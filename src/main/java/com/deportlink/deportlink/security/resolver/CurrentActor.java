package com.deportlink.deportlink.security.resolver;
import java.lang.annotation.*;
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentActor {}
