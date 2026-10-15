package com.emi.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to extract the subject from a token provided in HTTP headers or cookies.
 *
 * <p>Lookup order:
 * <ol>
 *     <li>HTTP Header (configured header name)</li>
 *     <li>Cookie (configured cookie name)</li>
 * </ol>
 *
 * <p>Examples:
 * <pre>
 *   &#64;TokenSubject("reg_token") String subject
 *   &#64;TokenSubject(value = "otp_token", required = true) String email
 *   &#64;TokenSubject(value = "token", required = false) String optionalUser
 *   &#64;TokenSubject(headerName = "X-Auth-Token", cookieName = "auth_token") String subject
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TokenSubject {

    /**
     * Name of the token in header and cookie.
     * Shorthand alias (e.g., &#64;TokenSubject("reg_token")).
     */
    String value() default "";

    /**
     * Explicit token name (alternative to value).
     */
    String name() default "";

    /**
     * Custom header name. Defaults to the token name.
     */
    String headerName() default "";

    /**
     * Custom cookie name. Defaults to the token name or "accessToken".
     */
    String cookieName() default "";

    /**
     * Whether the token is mandatory.
     * If true and the token is missing in both header and cookies, an exception is thrown.
     * Defaults to true.
     */
    boolean required() default true;

    /**
     * Custom exception message when the token is missing or invalid.
     */
    String message() default "";
}

