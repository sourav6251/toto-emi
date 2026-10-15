package com.emi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to extract a token from either Cookie or HTTP Header.
 *
 * <p>Examples:
 * <pre>
 *   &#64;AuthToken("otp_token")
 *   &#64;AuthToken(value = "otp_token", message = "Missing OTP verification token")
 *   &#64;AuthToken(name = "otp_token", message = "Please provide otp_token")
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthToken {

    /**
     * Shorthand token name (sets both cookie and header name).
     */
    String value() default "";

    /**
     * Explicit token name (alternative to value).
     */
    String name() default "";

    /**
     * Custom cookie name. Defaults to token name or "AccessToken".
     */
    String cookieName() default "";

    /**
     * Custom header name. Defaults to token name or "Authorization".
     */
    String headerName() default "";

    /**
     * Whether the token is mandatory. If true and missing, returns 401 Unauthorized.
     */
    boolean required() default true;

    /**
     * Custom error message when the token is not found.
     * If omitted, defaults to: "Missing '[token_name]' in request cookies or headers"
     */
    String message() default "";
}
