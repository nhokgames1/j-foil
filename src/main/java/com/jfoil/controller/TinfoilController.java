package com.jfoil.controller;

import com.jfoil.model.DownloadLog;
import com.jfoil.model.Subscription;
import com.jfoil.model.User;
import com.jfoil.repository.DownloadLogRepository;
import com.jfoil.repository.SubscriptionRepository;
import com.jfoil.repository.UserRepository;
import com.jfoil.service.FileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/tinfoil")
public class TinfoilController {

    private final FileService fileService;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DownloadLogRepository downloadLogRepository;

    public TinfoilController(FileService fileService, UserRepository userRepository,
                             SubscriptionRepository subscriptionRepository, DownloadLogRepository downloadLogRepository) {
        this.fileService = fileService;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.downloadLogRepository = downloadLogRepository;
    }

    @GetMapping("/index.json")
    public Map<String, Object> getIndex(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Subscription sub = subscriptionRepository.findById(user.getId()).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("directories", new ArrayList<>());

        if (sub == null || !sub.isActive() || sub.getExpiryDate().isBefore(LocalDateTime.now())) {
            response.put("files", new ArrayList<>());
            response.put("success", "Subscription Expired or Inactive. Please renew to access files.");
            return response;
        }

        response.put("files", fileService.getFileTree());
        response.put("success", "Welcome " + user.getUsername() + "! Your subscription is valid until " + sub.getExpiryDate().toLocalDate() + ".");

        return response;
    }

    @GetMapping("/files/{*filename}")
    public ResponseEntity<ResourceRegion> downloadFile(@RequestHeader HttpHeaders headers,
                                                       @PathVariable String filename,
                                                       HttpServletRequest request,
                                                       Authentication authentication) throws IOException {
        
        // Remove leading slash from filename
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }

        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Subscription sub = subscriptionRepository.findById(user.getId()).orElse(null);
        if (sub == null || !sub.isActive() || sub.getExpiryDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Path filePath = fileService.getFilePath(filename);
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            return ResponseEntity.notFound().build();
        }

        long contentLength = resource.contentLength();
        ResourceRegion region = resourceRegion(resource, headers);

        // Log download start only
        DownloadLog log = new DownloadLog();
        log.setUser(user);
        log.setFileName(filename);
        log.setFileSize(contentLength);
        log.setIpAddress(request.getRemoteAddr());
        downloadLogRepository.save(log);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(region);
    }

    private ResourceRegion resourceRegion(Resource resource, HttpHeaders headers) throws IOException {
        long contentLength = resource.contentLength();
        HttpRange range = headers.getRange().isEmpty() ? null : headers.getRange().get(0);

        if (range != null) {
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(1024 * 1024, end - start + 1); // 1MB chunks to avoid memory issues
            return new ResourceRegion(resource, start, rangeLength);
        } else {
            long rangeLength = Math.min(1024 * 1024, contentLength);
            return new ResourceRegion(resource, 0, rangeLength);
        }
    }
}
