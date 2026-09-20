package com.promorunner.controller;

import com.promorunner.security.CurrentUser;
import com.promorunner.service.ConsentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/consent")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @PostMapping
    public String submit(
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "consented", required = false) String consented,
            HttpServletRequest request) {
        // "skip" always declines; "consent" requires the checkbox
        boolean optedIn = "consent".equalsIgnoreCase(action)
                && "true".equalsIgnoreCase(consented);
        consentService.recordConsent(
                CurrentUser.requireId(),
                optedIn,
                request.getHeader("User-Agent")
        );
        return "redirect:/game";
    }

    @PostMapping("/withdraw")
    public String withdraw() {
        consentService.withdrawConsent(CurrentUser.requireId());
        return "redirect:/game";
    }
}
