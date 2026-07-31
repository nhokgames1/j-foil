package com.jfoil.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    @Value("${jfoil.storage.path}")
    private String storagePath;

    private final ConcurrentHashMap<String, Long> cachedFileTree = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> virtualToPhysicalMap = new ConcurrentHashMap<>();
    private WatchService watchService;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>();
    private Thread watcherThread;
    private volatile boolean running = true;

    private static String sanitizeFilename(String filename) {
        if (filename == null) return null;
        // Remove double closing brackets
        filename = filename.replaceAll("\\]+", "]");
        
        // Ensure matching brackets
        StringBuilder sb = new StringBuilder();
        boolean inBrackets = false;
        for (int i = 0; i < filename.length(); i++) {
            char c = filename.charAt(i);
            if (c == '[') {
                if (inBrackets) {
                    sb.append(']'); // close previous before opening new
                }
                sb.append('[');
                inBrackets = true;
            } else if (c == ']') {
                if (inBrackets) {
                    sb.append(']');
                    inBrackets = false;
                }
            } else {
                sb.append(c);
            }
        }
        if (inBrackets) {
            int extIdx = sb.lastIndexOf(".");
            if (extIdx != -1) {
                sb.insert(extIdx, ']');
            } else {
                sb.append(']');
            }
        }
        
        String result = sb.toString();
        result = result.replace("+", " ");
        result = result.replaceAll("\\s+", " ");
        return result;
    }

    private static String sanitizePath(String path) {
        if (path == null) return null;
        String[] parts = path.split("/");
        if (parts.length == 0) return path;
        parts[parts.length - 1] = sanitizeFilename(parts[parts.length - 1]);
        return String.join("/", parts);
    }

    @PostConstruct
    public void init() {
        Path rootPath = Paths.get(storagePath);
        if (!Files.exists(rootPath)) {
            try {
                Files.createDirectories(rootPath);
            } catch (IOException e) {
                log.error("Failed to create storage directory: {}", storagePath, e);
            }
        }
        
        // Initial scan of the directory
        scanStorageDirectory();

        // Start directory watcher in a background thread
        startDirectoryWatcher(rootPath);
    }

    @PreDestroy
    public void cleanup() {
        running = false;
        if (watcherThread != null) {
            watcherThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Error closing watch service", e);
            }
        }
    }

    private static boolean isValidSwitchFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".nsp") || name.endsWith(".nsz") || name.endsWith(".xci") || name.endsWith(".xcz");
    }

    public synchronized void scanStorageDirectory() {
        log.info("Scanning storage directory: {}", storagePath);
        Path rootPath = Paths.get(storagePath);
        if (!Files.exists(rootPath)) {
            return;
        }

        ConcurrentHashMap<String, Long> newTree = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, String> newVirtualMap = new ConcurrentHashMap<>();
        try (Stream<Path> paths = Files.walk(rootPath)) {
            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
            paths.filter(Files::isRegularFile)
                 .filter(FileService::isValidSwitchFile)
                 .forEach(path -> {
                      String relativePath = rootPath.relativize(path).toString().replace("\\", "/");
                      long size = path.toFile().length();
                      newTree.put(relativePath, size);
                      
                      String virtualPath = sanitizePath(relativePath);
                      newVirtualMap.put(virtualPath, relativePath);
                      
                      int currentCount = count.incrementAndGet();
                      if (currentCount % 100 == 0) {
                          log.info("Scan progress: {} files mapped so far...", currentCount);
                      }
                 });
            
            // Atomic swap to prevent incomplete states during requests
            cachedFileTree.clear();
            cachedFileTree.putAll(newTree);
            
            virtualToPhysicalMap.clear();
            virtualToPhysicalMap.putAll(newVirtualMap);
            
            log.info("Scan completed. Found {} files.", cachedFileTree.size());
        } catch (IOException e) {
            log.error("Failed to scan storage directory", e);
        }
    }

    public List<FileInfo> getFileTree() {
        return virtualToPhysicalMap.entrySet().stream()
                .map(entry -> new FileInfo(entry.getKey(), cachedFileTree.getOrDefault(entry.getValue(), 0L)))
                .collect(Collectors.toList());
    }

    public int getFileCount() {
        return cachedFileTree.size();
    }

    public String getTotalFileSizeFormatted() {
        long totalBytes = cachedFileTree.values().stream().mapToLong(Long::longValue).sum();
        return formatBytes(totalBytes);
    }

    private String formatBytes(long bytes) {
        if (bytes == 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public Path getFilePath(String filename) {
        String physicalFilename = virtualToPhysicalMap.getOrDefault(filename, filename);
        return Paths.get(storagePath, physicalFilename);
    }

    private void startDirectoryWatcher(Path rootPath) {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
            registerAll(rootPath);

            watcherThread = new Thread(() -> {
                long lastHeartbeat = System.currentTimeMillis();
                log.info("Directory watcher thread started.");
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        WatchKey key = watchService.poll(1, java.util.concurrent.TimeUnit.MINUTES);
                        
                        long now = System.currentTimeMillis();
                        if (now - lastHeartbeat >= 300000) { // 5 minutes heartbeat
                            log.info("Heartbeat: Directory watcher is active and monitoring {} directories.", watchKeys.size());
                            lastHeartbeat = now;
                        }

                        if (key == null) {
                            continue;
                        }

                        Path dir = watchKeys.get(key);
                        if (dir == null) {
                            key.cancel();
                            continue;
                        }

                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW) {
                                log.warn("Watch event overflow. Performing full scan.");
                                scanStorageDirectory();
                                continue;
                            }

                            @SuppressWarnings("unchecked")
                            WatchEvent<Path> ev = (WatchEvent<Path>) event;
                            Path name = ev.context();
                            Path child = dir.resolve(name);

                            // Resolve relative path to root path to match cache keys
                            String relativePath = rootPath.relativize(child).toString().replace("\\", "/");

                             if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                try {
                                    if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                                        registerAll(child);
                                        // A new directory was created, scan it to update cache for files inside it
                                        scanStorageDirectory();
                                    } else {
                                        if (isValidSwitchFile(child)) {
                                            cachedFileTree.put(relativePath, Files.size(child));
                                            virtualToPhysicalMap.put(sanitizePath(relativePath), relativePath);
                                            log.info("File created, added to cache: {}", relativePath);
                                        }
                                    }
                                } catch (IOException e) {
                                    log.error("Error handling ENTRY_CREATE for {}", child, e);
                                    // Fallback to full scan on error
                                    scanStorageDirectory();
                                }
                            } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                try {
                                    if (!Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                                        if (isValidSwitchFile(child)) {
                                            cachedFileTree.put(relativePath, Files.size(child));
                                            virtualToPhysicalMap.put(sanitizePath(relativePath), relativePath);
                                            log.info("File modified, updated cache: {}", relativePath);
                                        }
                                    }
                                } catch (IOException e) {
                                    log.error("Error handling ENTRY_MODIFY for {}", child, e);
                                }
                            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                boolean removed = false;
                                if (cachedFileTree.containsKey(relativePath)) {
                                    cachedFileTree.remove(relativePath);
                                    virtualToPhysicalMap.remove(sanitizePath(relativePath));
                                    removed = true;
                                }
                                
                                // Also remove nested files if a directory was deleted
                                String dirPrefix = relativePath + "/";
                                for (String cachedPath : cachedFileTree.keySet()) {
                                    if (cachedPath.startsWith(dirPrefix)) {
                                        cachedFileTree.remove(cachedPath);
                                        removed = true;
                                    }
                                }
                                
                                if (removed) {
                                    log.info("Removed path from cache: {}", relativePath);
                                }
                                
                                // Clean up watch keys for deleted directories
                                watchKeys.entrySet().removeIf(entry -> {
                                    if (entry.getValue().equals(child) || entry.getValue().startsWith(child)) {
                                        entry.getKey().cancel();
                                        return true;
                                    }
                                    return false;
                                });
                            }
                        }

                        boolean valid = key.reset();
                        if (!valid) {
                            watchKeys.remove(key);
                            if (watchKeys.isEmpty()) {
                                log.warn("All watch keys are invalid. Directory watcher stopped.");
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (ClosedWatchServiceException e) {
                        break;
                    } catch (Exception e) {
                        log.error("Error in directory watcher thread", e);
                    }
                }
            }, "J-Foil-FileWatcher");
            
            watcherThread.setDaemon(true);
            watcherThread.start();
            log.info("Directory watcher thread started.");
        } catch (IOException e) {
            log.error("Failed to initialize watch service", e);
        }
    }

    private void registerAll(final Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE, 
                StandardWatchEventKinds.ENTRY_DELETE, 
                StandardWatchEventKinds.ENTRY_MODIFY);
        watchKeys.put(key, dir);
        log.debug("Registered directory for watching: {}", dir);
    }

    public static class FileInfo {
        private String url;
        private long size;

        public FileInfo(String url, long size) {
            this.url = url;
            this.size = size;
        }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }
}
