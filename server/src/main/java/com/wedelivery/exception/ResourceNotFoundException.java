package com.wedelivery.exception;

/**
 * 请求的资源 (订单、用户等) 不存在，由 GlobalExceptionHandler 转换为 404。
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
