package com.promorunner.controller;

import com.promorunner.exception.InvalidMagicLinkException;
import com.promorunner.exception.NoPlaysRemainingException;
import com.promorunner.exception.SessionAlreadyCompletedException;
import com.promorunner.exception.SessionNotFoundException;
import com.promorunner.exception.TooManyRequestsException;
import com.promorunner.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TooManyRequestsException.class)
    public Object tooManyRequests(
            TooManyRequestsException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
            // 200 so HTMX swaps; HX-Retarget puts the form back in the login slot
            response.setHeader("HX-Retarget", "#login-form-slot");
            response.setHeader("HX-Reswap", "innerHTML");
            ModelAndView mav = new ModelAndView("fragments/modals :: login-form");
            mav.setStatus(HttpStatus.OK);
            mav.addObject("modalError", "rate_limit");
            String email = request.getParameter("email");
            if (email != null && !email.isBlank()) {
                mav.addObject("loginEmail", email.trim());
            }
            return mav;
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({
            SessionNotFoundException.class,
            UserNotFoundException.class
    })
    @ResponseBody
    public ResponseEntity<Map<String, String>> notFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({
            SessionAlreadyCompletedException.class,
            InvalidMagicLinkException.class,
            IllegalArgumentException.class
    })
    @ResponseBody
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NoPlaysRemainingException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> noPlays(NoPlaysRemainingException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", ex.getMessage(), "reason", "CONSENT_REQUIRED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a
                ));
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fields", fields
        ));
    }
}
