package com.jfoil.controller;

import com.jfoil.model.Subscription;
import com.jfoil.model.User;
import com.jfoil.repository.SubscriptionRepository;
import com.jfoil.repository.UserRepository;
import com.jfoil.service.FileService;
import com.jfoil.service.StatisticsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StatisticsService statisticsService;
    private final PasswordEncoder passwordEncoder;
    private final FileService fileService;

    public AdminController(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                           StatisticsService statisticsService, PasswordEncoder passwordEncoder,
                           FileService fileService) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.statisticsService = statisticsService;
        this.passwordEncoder = passwordEncoder;
        this.fileService = fileService;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("downloadsToday", statisticsService.getDownloadsToday());
        model.addAttribute("downloadsThisMonth", statisticsService.getDownloadsThisMonth());
        model.addAttribute("bandwidthToday", statisticsService.getBandwidthTodayFormatted());
        model.addAttribute("bandwidthThisMonth", statisticsService.getBandwidthThisMonthFormatted());
        model.addAttribute("topGames", statisticsService.getTopGames());
        model.addAttribute("activeUsersCount", subscriptionRepository.count());
        model.addAttribute("totalFiles", fileService.getFileCount());
        model.addAttribute("totalSize", fileService.getTotalFileSizeFormatted());
        return "admin/index";
    }

    @PostMapping("/files/refresh")
    public String refreshFiles() {
        fileService.scanStorageDirectory();
        return "redirect:/admin";
    }

    @GetMapping("/games")
    public String games(Model model) {
        model.addAttribute("files", fileService.getFileTree());
        return "admin/games";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("subscriptions", subscriptionRepository.findAll());
        return "admin/users";
    }

    @PostMapping("/users/add")
    public String addUser(@RequestParam String username, 
                          @RequestParam String password, 
                          @RequestParam String role,
                          @RequestParam String duration,
                          @RequestParam(required = false) String manualExpiry) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);

        Subscription sub = new Subscription();
        sub.setUser(user);
        
        LocalDateTime expiryDate;
        if ("manual".equals(duration) && manualExpiry != null && !manualExpiry.isBlank()) {
            try {
                expiryDate = java.time.LocalDate.parse(manualExpiry).atTime(23, 59, 59);
            } catch (Exception e) {
                expiryDate = LocalDateTime.now().plusMonths(1); // default fallback
            }
        } else {
            try {
                int months = Integer.parseInt(duration);
                expiryDate = LocalDateTime.now().plusMonths(months);
            } catch (Exception e) {
                expiryDate = LocalDateTime.now().plusMonths(1); // default fallback
            }
        }
        
        sub.setExpiryDate(expiryDate);
        sub.setActive(true);
        subscriptionRepository.save(sub);

        return "redirect:/admin/users";
    }

    @PostMapping("/users/reset-password")
    public String resetPassword(@RequestParam Long id, @RequestParam String password) {
        User user = userRepository.findById(id).orElseThrow();
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/toggle-sub")
    public String toggleSub(@RequestParam Long id) {
        Subscription sub = subscriptionRepository.findById(id).orElseThrow();
        sub.setActive(!sub.isActive());
        subscriptionRepository.save(sub);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/extend-sub")
    public String extendSub(@RequestParam Long id,
                            @RequestParam String duration,
                            @RequestParam(required = false) String manualExpiry) {
        Subscription sub = subscriptionRepository.findById(id).orElseThrow();
        
        LocalDateTime newExpiry;
        if ("manual".equals(duration) && manualExpiry != null && !manualExpiry.isBlank()) {
            try {
                newExpiry = java.time.LocalDate.parse(manualExpiry).atTime(23, 59, 59);
            } catch (Exception e) {
                newExpiry = LocalDateTime.now().plusMonths(1); // default fallback
            }
        } else {
            try {
                int months = Integer.parseInt(duration);
                // If current subscription is already expired, extend from now. Otherwise, extend from current expiry date.
                LocalDateTime baseDate = sub.getExpiryDate().isBefore(LocalDateTime.now()) ? LocalDateTime.now() : sub.getExpiryDate();
                newExpiry = baseDate.plusMonths(months);
            } catch (Exception e) {
                newExpiry = LocalDateTime.now().plusMonths(1); // default fallback
            }
        }
        
        sub.setExpiryDate(newExpiry);
        sub.setActive(true);
        subscriptionRepository.save(sub);
        return "redirect:/admin/users";
    }

    @PostMapping("/users/delete")
    public String deleteUser(@RequestParam Long id) {
        subscriptionRepository.deleteById(id);
        userRepository.deleteById(id);
        return "redirect:/admin/users";
    }
}
