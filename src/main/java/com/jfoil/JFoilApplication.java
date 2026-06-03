package com.jfoil;

import com.jfoil.model.Subscription;
import com.jfoil.model.User;
import com.jfoil.repository.SubscriptionRepository;
import com.jfoil.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@SpringBootApplication
@EnableCaching
public class JFoilApplication {
    public static void main(String[] args) {
        SpringApplication.run(JFoilApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, 
                                      SubscriptionRepository subscriptionRepository, 
                                      PasswordEncoder passwordEncoder) {
        return args -> {
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setRole("ADMIN");
                userRepository.save(admin);

                Subscription sub = new Subscription();
                sub.setUser(admin);
                sub.setExpiryDate(LocalDateTime.now().plusYears(100)); // Effectively never expires
                sub.setActive(true);
                subscriptionRepository.save(sub);
                
                System.out.println("=========================================================");
                System.out.println("Default admin user created! Username: admin | Password: admin123");
                System.out.println("=========================================================");
            }
        };
    }
}
