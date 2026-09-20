package com.promorunner.controller;

import com.promorunner.config.BrandProperties;
import com.promorunner.dto.GameConfigDto;
import com.promorunner.model.User;
import com.promorunner.repository.UserRepository;
import com.promorunner.security.CurrentUser;
import com.promorunner.security.UserPrincipal;
import com.promorunner.service.GameConfigService;
import com.promorunner.service.LeaderboardService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PageController {

    private final BrandProperties brandProperties;
    private final GameConfigService gameConfigService;
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;

    public PageController(
            BrandProperties brandProperties,
            GameConfigService gameConfigService,
            UserRepository userRepository,
            LeaderboardService leaderboardService) {
        this.brandProperties = brandProperties;
        this.gameConfigService = gameConfigService;
        this.userRepository = userRepository;
        this.leaderboardService = leaderboardService;
    }

    @ModelAttribute("brand")
    public BrandProperties brand() {
        return brandProperties;
    }

    @ModelAttribute("authenticated")
    public boolean authenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() instanceof UserPrincipal;
    }

    @GetMapping("/")
    public String index(
            Model model,
            @RequestParam(value = "modal", required = false) String modal,
            @RequestParam(value = "error", required = false) String error) {
        model.addAttribute("leaderboard", leaderboardService.getTopTen());
        model.addAttribute("openModal", modal);
        model.addAttribute("modalError", error);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            model.addAttribute("currentUser", principal);
        }
        return "index";
    }

    @GetMapping("/login")
    public String loginRedirect() {
        return "redirect:/?modal=login";
    }

    @GetMapping("/login/verify")
    public String loginVerifyRedirect() {
        return "redirect:/?modal=check-email";
    }

    @GetMapping("/consent")
    public String consentRedirect() {
        return "redirect:/game?modal=consent";
    }

    @GetMapping("/game")
    public String game(
            Model model,
            @RequestParam(value = "modal", required = false) String modal) {
        UserPrincipal principal = CurrentUser.require();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        Integer remaining = user.getFreePlaysRemaining();
        int playsRemaining = remaining == null ? -1 : remaining;
        model.addAttribute(
                "gameConfig",
                new GameConfigDto(gameConfigService.getConfig(), playsRemaining, principal.getId())
        );
        model.addAttribute("currentUser", principal);
        model.addAttribute("leaderboard", leaderboardService.getTopTen());
        model.addAttribute("openModal", modal);
        return "game";
    }

    @GetMapping("/leaderboard")
    public String leaderboard(Model model) {
        model.addAttribute("leaderboard", leaderboardService.getTopTen());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            model.addAttribute("currentUser", principal);
        }
        return "leaderboard";
    }

    @GetMapping("/admin")
    public String adminDashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/admin/assets")
    public String adminAssets(Model model) {
        model.addAttribute("config", gameConfigService.getConfig());
        return "admin/assets";
    }

    @GetMapping("/admin/leads")
    public String adminLeads() {
        return "admin/leads";
    }
}
