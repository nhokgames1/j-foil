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
                                      PasswordEncoder passwordEncoder,
                                      org.springframework.transaction.support.TransactionTemplate transactionTemplate) {
        return args -> {
            transactionTemplate.execute(status -> {
                User admin;
                if (userRepository.findByUsername("admin").isEmpty()) {
                    admin = new User();
                    admin.setUsername("admin");
                    admin.setPassword(passwordEncoder.encode("admin123"));
                    admin.setRole("ADMIN");
                    admin = userRepository.save(admin);
                } else {
                    admin = userRepository.findByUsername("admin").orElseThrow();
                }

                Subscription sub = subscriptionRepository.findById(admin.getId()).orElse(null);
                if (sub == null) {
                    sub = new Subscription();
                    sub.setUser(admin);
                }
                sub.setExpiryDate(LocalDateTime.now().plusYears(100)); // Effectively never expires
                sub.setActive(true);
                subscriptionRepository.save(sub);
                
                System.out.println("=========================================================");
                System.out.println("Default admin user ensured active! Username: admin | Password: admin123");
                System.out.println("=========================================================");
                return null;
            });
        };
    }
}
