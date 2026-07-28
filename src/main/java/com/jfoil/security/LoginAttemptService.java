package com.jfoil.security;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private final int MAX_ATTEMPT = 5;
    private final long BLOCK_DURATION_MS = 15 * 60 * 1000; // 15 minutes

    private final ConcurrentHashMap<String, Attempt> attemptsCache = new ConcurrentHashMap<>();

    public void loginFailed(String key) {
        attemptsCache.compute(key, (k, v) -> {
            if (v == null || System.currentTimeMillis() - v.timestamp > BLOCK_DURATION_MS) {
                return new Attempt(1, System.currentTimeMillis());
            }
            v.attempts++;
            v.timestamp = System.currentTimeMillis();
            return v;
        });
    }

    public boolean isBlocked(String key) {
        Attempt attempt = attemptsCache.get(key);
        if (attempt != null) {
            if (System.currentTimeMillis() - attempt.timestamp > BLOCK_DURATION_MS) {
                attemptsCache.remove(key);
                return false;
            }
            return attempt.attempts >= MAX_ATTEMPT;
        }
        return false;
    }

    private static class Attempt {
        int attempts;
        long timestamp;

        Attempt(int attempts, long timestamp) {
            this.attempts = attempts;
            this.timestamp = timestamp;
        }
    }
}
