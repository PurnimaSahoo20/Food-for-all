package com.pns.oauthserver.controller;



import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.pns.oauthserver.oauth.token.PasswordGrantAuthenticationToken;
import com.pns.oauthserver.service.UserCRUDService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pns.oauthserver.model.dto.UserProfileDTO;
import com.pns.oauthserver.model.dto.UserUpdateDTO;
import com.pns.oauthserver.model.entity.User;
import com.pns.oauthserver.repo.UserRepository;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api")
@Slf4j
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserCRUDService service;

    @GetMapping("/user/profile")
    public ResponseEntity<?> getUserProfile(Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }

            Jwt jwt = (Jwt) authentication.getPrincipal();
            String email = jwt.getClaimAsString("email");

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();
            UserProfileDTO profile = new UserProfileDTO();
            profile.setName(user.getName());
            profile.setEmail(user.getEmail());
            profile.setRole(user.getRole());
            profile.setRegion(user.getRegion());
            profile.setProfileImageUrl(user.getProfileImageUrl());
            profile.setInitialSetup(user.getRole() == null || user.getRegion() == null);

            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            log.error("Error fetching user profile: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    @PutMapping("/user/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody UserUpdateDTO updateDTO, Authentication authentication) {
        try {
            if (authentication == null || authentication.getPrincipal() == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
            }
            if(authentication instanceof JwtAuthenticationToken token) {
                String email=token.getToken().getSubject();
                log.info("{}",email);
            }

            Jwt jwt = (Jwt) authentication.getPrincipal();
            String email = jwt.getClaimAsString("email");

            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "User not found"));
            }

            User user = userOpt.get();

            // Validate role
            if (updateDTO.getRole() != null) {
                if (!updateDTO.getRole().equals("restaurant_owner") && !updateDTO.getRole().equals("volunteer")) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid role. Must be 'restaurant_owner' or 'volunteer'"));
                }
                user.setRole(updateDTO.getRole());
            }

            // Validate and set region
            if (updateDTO.getRegion() != null && !updateDTO.getRegion().trim().isEmpty()) {
                user.setRegion(updateDTO.getRegion().trim());
            }

            userRepository.save(user);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Profile updated successfully");
            response.put("role", user.getRole());
            response.put("region", user.getRegion());
            response.put("initialSetup", false);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating user profile: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
        }
    }

    @GetMapping("/hello")
    public ResponseEntity<?> hello(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(Map.of("message", "Hello, Guest!"));
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        return ResponseEntity.ok(Map.of(
                "message", "Hello, " + name + "!",
                "email", email
        ));
    }
}