package com.zsubera.jpa.exception;

/**
 * MyJpa-Plus 库的基础异常类。所有库特定的异常都继承此类，
 * 允许使用者通过捕获单一类型来处理所有库错误。
 */
public class MyJpaPlusException extends RuntimeException {

  /**
   * 构造带有错误消息的异常。
   *
   * @param message 错误消息
   */
  public MyJpaPlusException(String message) {
    super(message);
  }

  /**
   * 构造带有错误消息和原因的异常。
   *
   * @param message 错误消息
   * @param cause 原因
   */
  public MyJpaPlusException(String message, Throwable cause) {
    super(message, cause);
  }
}
