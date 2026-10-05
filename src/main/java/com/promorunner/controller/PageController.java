package com.promorunner.controller;

import com.promorunner.config.AppProperties;
import com.promorunner.config.BrandProperties;
import com.promorunner.dto.GameConfigDto;
import com.promorunner.model.User;
import com.promorunner.repository.UserRepository;
import com.promorunner.security.CurrentUser;
import com.promorunner.security.UserPrincipal;
import com.promorunner.service.AccountService;
import com.promorunner.service.CampaignService;
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
    private final UserRepository userRepository;
    private final LeaderboardService leaderboardService;
    private final AccountService accountService;
    private final CampaignService campaignService;

    public PageController(
            BrandProperties brandProperties,
            UserRepository userRepository,
            LeaderboardService leaderboardService,
            AccountService accountService,
            CampaignService campaignService) {
        this.brandProperties = brandProperties;
        this.userRepository = userRepository;
        this.leaderboardService = leaderboardService;
        this.accountService = accountService;
        this.campaignService = campaignService;
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

    @ModelAttribute("accountUser")
    public User accountUser() {
        if (!authenticated()) {
            return null;
        }
        return accountService.requireUser(CurrentUser.requireId());
    }

    @ModelAttribute("campaignOpen")
    public boolean campaignOpen() {
        return campaignService.isOpen();
    }

    @ModelAttribute("campaignStartDate")
    public String campaignStartDate() {
        return campaignService.formatStartDate();
    }

    @ModelAttribute("campaignEndDate")
    public String campaignEndDate() {
        return campaignService.formatEndDate();
    }

    @ModelAttribute("prizes")
    public AppProperties.Prizes prizes() {
        return campaignService.getPrizes();
    }

    @GetMapping("/")
    public String index(
            Model model,
            @RequestParam(value = "modal", required = false) String modal,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "reset", required = false) String reset) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthed = auth != null && auth.getPrincipal() instanceof UserPrincipal;
        if (isAuthed && ("login".equals(modal) || "register".equals(modal) || "forgot".equals(modal))) {
            return "redirect:/game";
        }
        model.addAttribute("openModal", modal);
        model.addAttribute("modalError", error);
        model.addAttribute("resetOk", "ok".equals(reset));
        if (isAuthed) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            model.addAttribute("currentUser", principal);
            model.addAttribute("leaderboard", leaderboardService.forViewer(principal.getId()));
        } else {
            model.addAttribute("leaderboard", leaderboardService.getTop());
        }
        return "index";
    }

    @GetMapping("/login")
    public String loginRedirect() {
        if (authenticated()) {
            return "redirect:/game";
        }
        return "redirect:/?modal=login";
    }

    @GetMapping("/consent")
    public String consentRedirect() {
        return "redirect:/game?modal=consent";
    }

    @GetMapping("/game")
    public String game(
            Model model,
            @RequestParam(value = "modal", required = false) String modal) {
        if (!campaignService.isOpen()) {
            return "redirect:/";
        }
        UserPrincipal principal = CurrentUser.require();
        User user = userRepository.findById(principal.getId()).orElseThrow();
        Integer remaining = user.getFreePlaysRemaining();
        int playsRemaining = remaining == null ? -1 : remaining;
        model.addAttribute("gameConfig", new GameConfigDto(playsRemaining));
        model.addAttribute("currentUser", principal);
        model.addAttribute("leaderboard", leaderboardService.forViewer(principal.getId()));
        if (user.getDisplayName() == null || user.getDisplayName().isBlank()) {
            model.addAttribute("openModal", "username");
        } else {
            model.addAttribute("openModal", modal);
        }
        return "game";
    }

    @GetMapping("/leaderboard")
    public String leaderboard(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            model.addAttribute("currentUser", principal);
            model.addAttribute("leaderboard", leaderboardService.forViewer(principal.getId()));
        } else {
            model.addAttribute("leaderboard", leaderboardService.getTop());
        }
        return "leaderboard";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }

    @GetMapping("/admin")
    public String adminDashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/admin/assets")
    public String adminAssets() {
        return "admin/assets";
    }

    @GetMapping("/admin/leads")
    public String adminLeads() {
        return "admin/leads";
    }
}
