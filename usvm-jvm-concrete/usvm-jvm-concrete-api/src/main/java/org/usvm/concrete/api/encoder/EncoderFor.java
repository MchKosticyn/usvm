package org.usvm.concrete.api.encoder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
public @interface EncoderFor {
    Class<?> value();
}
