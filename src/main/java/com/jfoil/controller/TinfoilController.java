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
import org.springframework.core.io.FileSystemResource;
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

    private String encodePath(String path) {
        try {
            return org.springframework.web.util.UriUtils.encodePath(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return path;
        }
    }

    @GetMapping({ "", "/", "/index.json" })
    public Map<String, Object> getIndex(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Subscription sub = subscriptionRepository.findById(user.getId()).orElse(null);

        Map<String, Object> response = new HashMap<>();

        if (sub == null || !sub.isActive() || sub.getExpiryDate().isBefore(LocalDateTime.now())) {
            response.put("directories", new ArrayList<>());
            response.put("files", new ArrayList<>());
            response.put("success",
                    "Goi dang ky da het han hoac chua kich hoat. Vui long lien he facebook.com/vieetvungve hoac Zalo 0357394235 de gia han.");
            return response;
        }

        List<String> directories = fileService.getSubdirectories("").stream()
                .map(dir -> "/tinfoil/dir/" + encodePath(dir))
                .toList();
        response.put("directories", directories);

        List<com.jfoil.service.FileService.FileInfo> files = fileService.getFileTree().stream()
                .filter(file -> {
                    String ext = file.getUrl().toLowerCase();
                    return ext.endsWith(".nsp") || ext.endsWith(".nsz") || ext.endsWith(".xci") || ext.endsWith(".xcz");
                })
                .map(file -> new com.jfoil.service.FileService.FileInfo("/tinfoil/files/" + encodePath(file.getUrl()), file.getSize()))
                .toList();
        response.put("files", files);
        response.put("success", "Chao mung " + user.getUsername() + "! Goi dang ky cua ban co hieu luc den ngay "
                + sub.getExpiryDate().toLocalDate() + ".");

        return response;
    }

    @GetMapping({"/dir/{*path}"})
    public Map<String, Object> getDirectoryIndex(@PathVariable String path, Authentication authentication) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.endsWith("/index.json")) {
            path = path.substring(0, path.length() - 11);
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Subscription sub = subscriptionRepository.findById(user.getId()).orElse(null);

        Map<String, Object> response = new HashMap<>();

        if (sub == null || !sub.isActive() || sub.getExpiryDate().isBefore(LocalDateTime.now())) {
            response.put("directories", new ArrayList<>());
            response.put("files", new ArrayList<>());
            return response;
        }

        String finalPath = path;
        List<String> directories = fileService.getSubdirectories(finalPath).stream()
                .map(dir -> "/tinfoil/dir/" + encodePath(finalPath + "/" + dir))
                .toList();
        response.put("directories", directories);

        List<com.jfoil.service.FileService.FileInfo> files = fileService.getFilesInDirectory(finalPath).stream()
                .map(file -> new com.jfoil.service.FileService.FileInfo("/tinfoil/files/" + encodePath(file.getUrl()), file.getSize()))
                .toList();
        response.put("files", files);
        
        response.put("success", "/" + finalPath);

        return response;
    }

    @RequestMapping(value = "/files/{*filename}", method = { RequestMethod.GET, RequestMethod.HEAD })
    public void downloadFile(@PathVariable String filename,
            HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            Authentication authentication) throws IOException {

        // Remove leading slash from filename
        if (filename.startsWith("/")) {
            filename = filename.substring(1);
        }

        // URL decode filename to support both encoded and decoded path requests
        try {
            filename = org.springframework.web.util.UriUtils.decode(filename, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // fallback to original path if decoding fails
        }

        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        Subscription sub = subscriptionRepository.findById(user.getId()).orElse(null);
        if (sub == null || !sub.isActive() || sub.getExpiryDate().isBefore(LocalDateTime.now())) {
            response.setStatus(HttpStatus.PAYMENT_REQUIRED.value());
            response.setHeader("X-Subscription-Error", "Subscription expired or inactive. Contact facebook.com/vieetvungve to renew.");
            return;
        }

        Path filePath = fileService.getFilePath(filename);
        log.info("Download requested for file: '{}' (user: '{}', ip: '{}'). Resolving to path: '{}'",
                filename, user.getUsername(), request.getRemoteAddr(), filePath.toAbsolutePath());
        
        java.io.File file = filePath.toFile();
        if (!file.exists() || !file.canRead()) {
            log.warn("File not found or not readable: '{}' -> Resolved path: '{}'", filename, filePath.toAbsolutePath());
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        long contentLength = file.length();
        String rangeHeader = request.getHeader(HttpHeaders.RANGE);

        // Log download start only for GET requests and only for full file or the first
        // block/chunk of parallel range downloads
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            if (rangeHeader == null || rangeHeader.isBlank() || rangeHeader.startsWith("bytes=0-") || rangeHeader.startsWith("bytes=0/")) {
                DownloadLog logEntry = new DownloadLog();
                logEntry.setUser(user);
                logEntry.setFileName(filename);
                logEntry.setFileSize(contentLength);
                logEntry.setIpAddress(request.getRemoteAddr());
                downloadLogRepository.save(logEntry);
            }
        }

        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (rangeHeader != null && !rangeHeader.isBlank()) {
            try {
                java.util.List<org.springframework.http.HttpRange> ranges = org.springframework.http.HttpRange.parseRanges(rangeHeader);
                if (!ranges.isEmpty()) {
                    org.springframework.http.HttpRange range = ranges.get(0);
                    long start = range.getRangeStart(contentLength);
                    long end = range.getRangeEnd(contentLength);
                    long rangeLength = end - start + 1;

                    response.setStatus(HttpStatus.PARTIAL_CONTENT.value());
                    response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + contentLength);
                    response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(rangeLength));

                    if ("GET".equalsIgnoreCase(request.getMethod())) {
                        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r");
                             java.io.OutputStream os = response.getOutputStream()) {
                            raf.seek(start);
                            byte[] buffer = new byte[65536];
                            long bytesToRead = rangeLength;
                            int bytesRead;
                            while (bytesToRead > 0 && (bytesRead = raf.read(buffer, 0, (int) Math.min(buffer.length, bytesToRead))) != -1) {
                                os.write(buffer, 0, bytesRead);
                                bytesToRead -= bytesRead;
                            }
                            os.flush();
                        } catch (IOException e) {
                            // Client disconnected early, perfectly normal
                        }
                    }
                    return;
                }
            } catch (IllegalArgumentException e) {
                response.setStatus(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE.value());
                return;
            }
        }

        response.setStatus(HttpStatus.OK.value());
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength));

        if ("GET".equalsIgnoreCase(request.getMethod())) {
            try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
                 java.io.OutputStream os = response.getOutputStream()) {
                byte[] buffer = new byte[65536];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            } catch (IOException e) {
                // Client disconnected early
            }
        }
    }
}
