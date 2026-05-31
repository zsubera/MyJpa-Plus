package com.zsubera.jpa.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypt {
    /**
     * 加密算法标识。当前仅支持 AES-GCM，此属性保留供未来扩展使用。
     *
     * @return 算法标识
     * @deprecated 当前版本固定使用 AES-256-GCM 算法，此属性暂未实现。保留供未来多算法支持时使用。
     */
    @Deprecated(since = "1.2.0")
    String algorithm() default "AES";
}
