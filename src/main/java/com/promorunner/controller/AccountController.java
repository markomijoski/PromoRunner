package com.promorunner.controller;

import com.promorunner.dto.AccountExportDto;
import com.promorunner.security.CurrentUser;
import com.promorunner.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public String accountPage() {
        return "redirect:/?modal=account";
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountExportDto> export() {
        AccountExportDto dto = accountService.export(CurrentUser.requireId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"amsm-account-export.json\"")
                .body(dto);
    }

    @PostMapping("/leaderboard-visibility")
    public String setLeaderboardVisibility(@RequestParam("visible") String visible) {
        boolean show = "true".equalsIgnoreCase(visible);
        accountService.setLeaderboardVisible(CurrentUser.requireId(), show);
        return "redirect:/?modal=account";
    }

    @PostMapping("/delete")
    public String delete(HttpServletRequest request, HttpServletResponse response) {
        long userId = CurrentUser.requireId();
        accountService.deleteAccount(userId);
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        return "redirect:/";
    }
}
