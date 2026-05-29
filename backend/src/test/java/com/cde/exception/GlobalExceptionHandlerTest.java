package com.cde.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsExpectedExceptionTypesToJsonResponses() {
        assertThat(handler.handleBadCredentials(new BadCredentialsException("bad")).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("denied")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleBusinessException(new BusinessException(HttpStatus.NOT_FOUND, "missing")).getBody().getCode())
                .isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(handler.handleBusinessException(new BusinessException(HttpStatus.BAD_REQUEST, "bad")).getBody().getCode())
                .isEqualTo("INVALID_REQUEST");
        assertThat(handler.handleBusinessException(new BusinessException(HttpStatus.UNAUTHORIZED, "bad")).getBody().getCode())
                .isEqualTo("UNAUTHORIZED");
        assertThat(handler.handleBusinessException(new BusinessException(HttpStatus.FORBIDDEN, "bad")).getBody().getCode())
                .isEqualTo("ACCESS_DENIED");
        assertThat(handler.handleBusinessException(new BusinessException(HttpStatus.BAD_GATEWAY, "bad")).getBody().getCode())
                .isEqualTo("BUSINESS_ERROR");
        assertThat(handler.handleUnreadableBody(new HttpMessageNotReadableException("bad")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleException(new RuntimeException("boom")).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void validationUsesFirstFieldErrorOrFallback() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "target");
        binding.addError(new FieldError("target", "name", "name required"));
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(null, binding);

        assertThat(handler.handleValidation(exception).getBody().getMessage()).isEqualTo("name required");

        BeanPropertyBindingResult empty = new BeanPropertyBindingResult(new Object(), "target");
        MethodArgumentNotValidException fallback = new MethodArgumentNotValidException(null, empty);
        assertThat(handler.handleValidation(fallback).getBody().getCode()).isEqualTo("INVALID_REQUEST");
    }
}
