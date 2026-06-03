package com.jfoil.controller;

import com.jfoil.model.Subscription;
import com.jfoil.model.User;
import com.jfoil.repository.SubscriptionRepository;
import com.jfoil.repository.UserRepository;
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

    public AdminController(UserRepository userRepository, SubscriptionRepository subscriptionRepository,
                           StatisticsService statisticsService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.statisticsService = statisticsService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("downloadsToday", statisticsService.getDownloadsToday());
        model.addAttribute("downloadsThisMonth", statisticsService.getDownloadsThisMonth());
        model.addAttribute("bandwidthToday", statisticsService.getBandwidthTodayFormatted());
        model.addAttribute("bandwidthThisMonth", statisticsService.getBandwidthThisMonthFormatted());
        model.addAttribute("topGames", statisticsService.getTopGames());
        model.addAttribute("activeUsersCount", subscriptionRepository.count());
        return "admin/index";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("subscriptions", subscriptionRepository.findAll());
        return "admin/users";
    }

    @PostMapping("/users/add")
    public String addUser(@RequestParam String username, @RequestParam String password, @RequestParam String role) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        userRepository.save(user);

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setExpiryDate(LocalDateTime.now().plusMonths(1));
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
    public String extendSub(@RequestParam Long id) {
        Subscription sub = subscriptionRepository.findById(id).orElseThrow();
        if (sub.getExpiryDate().isBefore(LocalDateTime.now())) {
            sub.setExpiryDate(LocalDateTime.now().plusMonths(1));
        } else {
            sub.setExpiryDate(sub.getExpiryDate().plusMonths(1));
        }
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
