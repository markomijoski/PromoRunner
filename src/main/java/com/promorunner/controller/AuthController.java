package com.promorunner.controller;

import com.promorunner.exception.InvalidMagicLinkException;
import com.promorunner.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService, SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String login(@RequestParam("email") String email, Model model) {
        authService.requestMagicLink(email);
        model.addAttribute("email", email.trim());
        return "fragments/modals :: check-email";
    }

    @GetMapping("/verify")
    public String verify(
            @RequestParam(value = "token", required = false) String token,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            String redirect = authService.verifyToken(token);
            securityContextRepository.saveContext(
                    SecurityContextHolder.getContext(),
                    request,
                    response
            );
            if ("/consent".equals(redirect)) {
                return "redirect:/game?modal=consent";
            }
            return "redirect:/game";
        } catch (InvalidMagicLinkException ex) {
            return "redirect:/?modal=login&error=invalid_token";
        }
    }
}
