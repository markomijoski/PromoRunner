package com.promorunner.controller;

import com.promorunner.config.BrandProperties;
import com.promorunner.exception.InvalidResetTokenException;
import com.promorunner.security.CurrentUser;
import com.promorunner.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;
    private final BrandProperties brandProperties;

    public AuthController(
            AuthService authService,
            SecurityContextRepository securityContextRepository,
            BrandProperties brandProperties) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
        this.brandProperties = brandProperties;
    }

    @ModelAttribute("brand")
    public BrandProperties brand() {
        return brandProperties;
    }

    @ModelAttribute("authenticated")
    public boolean authenticated() {
        return CurrentUser.isAuthenticated();
    }

    @PostMapping(value = "/register", produces = MediaType.TEXT_HTML_VALUE)
    public Object register(
            @RequestParam("email") String email,
            @RequestParam("username") String username,
            @RequestParam("password") String password,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (CurrentUser.isAuthenticated()) {
            response.setHeader("HX-Redirect", "/game");
            return ResponseEntity.ok().build();
        }
        String redirect = authService.register(email, username, password);
        securityContextRepository.saveContext(
                SecurityContextHolder.getContext(),
                request,
                response
        );
        response.setHeader("HX-Redirect", redirect);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public Object login(
            @RequestParam("email") String email,
            @RequestParam("password") String password,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (CurrentUser.isAuthenticated()) {
            response.setHeader("HX-Redirect", "/game");
            return ResponseEntity.ok().build();
        }
        String redirect = authService.login(email, password);
        securityContextRepository.saveContext(
                SecurityContextHolder.getContext(),
                request,
                response
        );
        response.setHeader("HX-Redirect", redirect);
        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/forgot", produces = MediaType.TEXT_HTML_VALUE)
    public String forgot(
            @RequestParam("email") String email,
            Model model) {
        authService.requestPasswordReset(email);
        model.addAttribute("email", email == null ? "" : email.trim());
        return "fragments/modals :: forgot-sent";
    }

    @GetMapping("/reset")
    public String resetPage(
            @RequestParam(value = "token", required = false) String token,
            Model model) {
        if (CurrentUser.isAuthenticated()) {
            return "redirect:/game";
        }
        try {
            authService.requireValidResetToken(token);
            model.addAttribute("token", token.trim());
            model.addAttribute("tokenValid", true);
        } catch (InvalidResetTokenException ex) {
            model.addAttribute("tokenValid", false);
            model.addAttribute("errorMessage", ex.getMessage());
        }
        return "reset-password";
    }

    @PostMapping("/reset")
    public String resetPassword(
            @RequestParam("token") String token,
            @RequestParam("password") String password,
            @RequestParam("confirmPassword") String confirmPassword,
            Model model) {
        if (CurrentUser.isAuthenticated()) {
            return "redirect:/game";
        }
        if (password == null || !password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("tokenValid", true);
            model.addAttribute("formError", "Лозинките не се совпаѓаат");
            return "reset-password";
        }
        try {
            authService.resetPassword(token, password);
            return "redirect:/?modal=login&reset=ok";
        } catch (InvalidResetTokenException ex) {
            model.addAttribute("tokenValid", false);
            model.addAttribute("errorMessage", ex.getMessage());
            return "reset-password";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("token", token);
            model.addAttribute("tokenValid", true);
            model.addAttribute("formError", ex.getMessage());
            return "reset-password";
        }
    }

    @PostMapping(value = "/username", produces = MediaType.TEXT_HTML_VALUE)
    public String setUsername(
            @RequestParam("username") String username,
            HttpServletResponse response) {
        authService.setUsername(CurrentUser.requireId(), username);
        response.setHeader("HX-Redirect", "/game");
        return "fragments/modals :: login-form";
    }
}
