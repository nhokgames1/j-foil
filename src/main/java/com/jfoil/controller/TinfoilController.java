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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tinfoil")
public class TinfoilController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TinfoilController.class);

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

    @GetMapping({"", "/", "/index.json"})
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

        List<com.jfoil.service.FileService.FileInfo> files = fileService.getFileTree().stream()
                .map(file -> new com.jfoil.service.FileService.FileInfo("files/" + file.getUrl(), file.getSize()))
                .toList();
        response.put("files", files);
        response.put("success", "Welcome " + user.getUsername() + "! Your subscription is valid until " + sub.getExpiryDate().toLocalDate() + ".");

        return response;
    }

    @RequestMapping(value = "/files/{*filename}", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<?> downloadFile(@RequestHeader HttpHeaders headers,
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
        log.info("Download requested for file: '{}' (user: '{}', ip: '{}'). Resolving to path: '{}'", 
                filename, user.getUsername(), request.getRemoteAddr(), filePath.toAbsolutePath());
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            log.warn("File not found or not readable: '{}' -> Resolved path: '{}'", filename, filePath.toAbsolutePath());
            return ResponseEntity.notFound().build();
        }

        long contentLength = resource.contentLength();

        // Log download start only for GET requests
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            DownloadLog log = new DownloadLog();
            log.setUser(user);
            log.setFileName(filename);
            log.setFileSize(contentLength);
            log.setIpAddress(request.getRemoteAddr());
            downloadLogRepository.save(log);
        }

        List<HttpRange> ranges = headers.getRange();
        if (ranges == null || ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(contentLength)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } else {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = end - start + 1;
            ResourceRegion region = new ResourceRegion(resource, start, rangeLength);
            
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(region);
        }
    }
}
