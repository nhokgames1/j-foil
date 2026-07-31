package com.jfoil.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Enumeration;

@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!request.getRequestURI().startsWith("/tinfoil/files/")) {
            filterChain.doFilter(request, response);
            return;
        }

        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("--- [").append(LocalDateTime.now()).append("] ---\n");
        logBuilder.append("REQUEST: ").append(request.getMethod()).append(" ").append(request.getRequestURI())
                .append("\n");

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            logBuilder.append("Req Header - ").append(headerName).append(": ").append(request.getHeader(headerName))
                    .append("\n");
        }

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            logBuilder.append("EXCEPTION: ").append(e.toString()).append("\n");
            throw e;
        } finally {
            logBuilder.append("RESPONSE STATUS: ").append(response.getStatus()).append("\n");
            for (String headerName : response.getHeaderNames()) {
                logBuilder.append("Res Header - ").append(headerName).append(": ")
                        .append(response.getHeader(headerName)).append("\n");
            }
            logBuilder.append("----------------------------------------\n\n");

            try (PrintWriter out = new PrintWriter(new FileWriter("C:\\Code\\J-Foil\\http-debug.log", true))) {
                out.println(logBuilder.toString());
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
