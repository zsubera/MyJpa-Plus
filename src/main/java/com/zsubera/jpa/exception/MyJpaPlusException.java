package com.zsubera.jpa.exception;

/**
 * Base exception for the MyJpa-Plus library. All library-specific exceptions extend this class,
 * allowing consumers to catch a single type for all library errors.
 */
public class MyJpaPlusException extends RuntimeException {

  public MyJpaPlusException(String message) {
    super(message);
  }

  public MyJpaPlusException(String message, Throwable cause) {
    super(message, cause);
  }
}
