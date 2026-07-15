package com.jfoil.controller;

import com.jfoil.model.Subscription;
import com.jfoil.model.User;
import com.jfoil.repository.DownloadLogRepository;
import com.jfoil.repository.SubscriptionRepository;
import com.jfoil.repository.UserRepository;
import com.jfoil.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TinfoilController.class)
public class TinfoilControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileService fileService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private SubscriptionRepository subscriptionRepository;

    @MockBean
    private DownloadLogRepository downloadLogRepository;

    @MockBean
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @MockBean
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private User mockUser;
    private Subscription mockSubscription;
    private byte[] testFileContent;
    private Path tempFilePath;

    @BeforeEach
    public void setup(@TempDir Path tempDir) throws Exception {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setRole("USER");

        mockSubscription = new Subscription();
        mockSubscription.setUser(mockUser);
        mockSubscription.setActive(true);
        mockSubscription.setExpiryDate(LocalDateTime.now().plusDays(10));

        testFileContent = new byte[100];
        for (int i = 0; i < 100; i++) {
            testFileContent[i] = (byte) i;
        }

        tempFilePath = tempDir.resolve("testgame.nsp");
        Files.write(tempFilePath, testFileContent);

        Mockito.when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
        Mockito.when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(mockSubscription));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    public void testDownloadFullFile() throws Exception {
        Mockito.when(fileService.getFilePath("testgame.nsp")).thenReturn(tempFilePath);

        MvcResult result = mockMvc.perform(get("/tinfoil/files/testgame.nsp"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "100"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/octet-stream"))
                .andReturn();

        assertArrayEquals(testFileContent, result.getResponse().getContentAsByteArray());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    public void testDownloadRangeMiddle() throws Exception {
        Mockito.when(fileService.getFilePath("testgame.nsp")).thenReturn(tempFilePath);

        MvcResult result = mockMvc.perform(get("/tinfoil/files/testgame.nsp")
                        .header(HttpHeaders.RANGE, "bytes=10-20"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 10-20/100"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "11"))
                .andReturn();

        byte[] expected = new byte[11];
        System.arraycopy(testFileContent, 10, expected, 0, 11);
        assertArrayEquals(expected, result.getResponse().getContentAsByteArray());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    public void testDownloadRangeSuffix() throws Exception {
        Mockito.when(fileService.getFilePath("testgame.nsp")).thenReturn(tempFilePath);

        MvcResult result = mockMvc.perform(get("/tinfoil/files/testgame.nsp")
                        .header(HttpHeaders.RANGE, "bytes=90-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 90-99/100"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "10"))
                .andReturn();

        byte[] expected = new byte[10];
        System.arraycopy(testFileContent, 90, expected, 0, 10);
        assertArrayEquals(expected, result.getResponse().getContentAsByteArray());
    }
}
