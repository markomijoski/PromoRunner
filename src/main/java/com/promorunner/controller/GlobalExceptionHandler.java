package com.promorunner.controller;

import com.promorunner.exception.InvalidResetTokenException;
import com.promorunner.exception.CampaignClosedException;
import com.promorunner.exception.SessionAlreadyCompletedException;
import com.promorunner.exception.SessionNotFoundException;
import com.promorunner.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, BadCredentialsException.class})
    public Object badArgument(
            RuntimeException ex,
            HttpServletRequest request,
            HttpServletResponse response) {
        if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) {
            String path = request.getRequestURI();
            if (path != null && path.contains("/auth/username")) {
                response.setHeader("HX-Retarget", "#username-form-slot");
                response.setHeader("HX-Reswap", "innerHTML");
                ModelAndView mav = new ModelAndView("fragments/modals :: username-form");
                mav.setStatus(HttpStatus.OK);
                mav.addObject("usernameError", ex.getMessage());
                return mav;
            }
            return authFormError(request, response, "validation", ex.getMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
    }

    private ModelAndView authFormError(
            HttpServletRequest request,
            HttpServletResponse response,
            String modalError,
            String validationMessage) {
        String path = request.getRequestURI();
        String fragment;
        String slot;
        if (path != null && path.contains("/auth/register")) {
            fragment = "fragments/modals :: register-form";
            slot = "#register-form-slot";
        } else if (path != null && path.contains("/auth/forgot")) {
            fragment = "fragments/modals :: forgot-form";
            slot = "#forgot-form-slot";
        } else {
            fragment = "fragments/modals :: login-form";
            slot = "#login-form-slot";
        }

        response.setHeader("HX-Retarget", slot);
        response.setHeader("HX-Reswap", "innerHTML");
        ModelAndView mav = new ModelAndView(fragment);
        mav.setStatus(HttpStatus.OK);
        mav.addObject("modalError", modalError);
        if (validationMessage != null) {
            mav.addObject("validationError", validationMessage);
        }
        String email = request.getParameter("email");
        if (email != null && !email.isBlank()) {
            mav.addObject("loginEmail", email.trim());
        }
        String username = request.getParameter("username");
        if (username != null && !username.isBlank()) {
            mav.addObject("loginUsername", username.trim());
        }
        return mav;
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
            InvalidResetTokenException.class,
            CampaignClosedException.class
    })
    @ResponseBody
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", ex.getMessage()));
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
