package com.example.springjpa.web

import com.example.springjpa.application.exception.NotFoundException
import com.example.springjpa.web.dto.ErrorResponse
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.BindException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(NotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(ex: NotFoundException): ErrorResponse =
        ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = HttpStatus.NOT_FOUND.reasonPhrase,
            message = ex.message ?: "Resource not found",
        )

    @ExceptionHandler(MethodArgumentNotValidException::class, BindException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: Exception): ErrorResponse {
        val message = when (ex) {
            is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors.firstOrNull()?.let {
                "${it.field}: ${it.defaultMessage ?: "invalid value"}"
            }

            is BindException -> ex.bindingResult.fieldErrors.firstOrNull()?.let {
                "${it.field}: ${it.defaultMessage ?: "invalid value"}"
            }

            else -> null
        } ?: "Validation error"

        return ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = message,
        )
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        DataIntegrityViolationException::class,
        IllegalArgumentException::class,
        MethodArgumentTypeMismatchException::class,
    )
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleBadRequest(ex: Exception): ErrorResponse =
        ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = ex.message ?: "Bad request",
        )
}
