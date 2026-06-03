package com.jfoil.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FileService {

    @Value("${jfoil.storage.path}")
    private String storagePath;

    @Cacheable("fileTree")
    public List<FileInfo> getFileTree() {
        List<FileInfo> fileInfos = new ArrayList<>();
        Path rootPath = Paths.get(storagePath);
        
        if (!Files.exists(rootPath)) {
            return fileInfos;
        }

        try (Stream<Path> paths = Files.walk(rootPath)) {
            fileInfos = paths
                    .filter(Files::isRegularFile)
                    .map(path -> {
                        String relativePath = rootPath.relativize(path).toString().replace("\\", "/");
                        long size = path.toFile().length();
                        return new FileInfo(relativePath, size);
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileInfos;
    }

    public Path getFilePath(String filename) {
        return Paths.get(storagePath, filename);
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
