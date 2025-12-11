package com.pns.oauthserver.service;

import com.pns.oauthserver.model.entity.User;
import com.pns.oauthserver.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class UserCRUDService {
    private final UserRepository userRepository;

    public UserCRUDService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean createUserIfNotExist(OAuth2User oauth2User) {
        if(userRepository.findByEmail(oauth2User.getAttribute("email")).isEmpty()) {
            User user = new User();
            user.setName(oauth2User.getAttribute("name"));
            user.setEmail(oauth2User.getAttribute("email"));
            user.setProfileImageUrl(saveAndGenerateProfilePicturePath(user.getEmail(),oauth2User.getAttribute("picture")));
            try{
                userRepository.save(user);
            }catch(Exception e){
                log.error("Unable to save user into DB: {}", e.getMessage());
                return false;
            }
        }
        return true;
    }


    private String saveAndGenerateProfilePicturePath(String email, String pictureUrl) {
        try{
            Path uploadDir = Paths.get("uploads");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String filename = email.replaceAll("[^a-zA-Z0-9]", "_") + ".jpg";
            Path filePath = uploadDir.resolve(filename);

            if (!Files.exists(filePath)) {
                try (InputStream in = new URI(pictureUrl).toURL().openStream()) {
                    Files.copy(in, filePath);
                }
            }
            return "uploads/" + filename;
        }catch (IOException | URISyntaxException e){
            log.error("Unable to save user profile picture: {}", e.getMessage());
        }
        return "uploads/default.jpg";
    }
}
