package com.pns.oauthserver.model.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name= "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private int pk;
    private String name;
    private String password;
    private String email;
    private String role;
    private String region;
    private String profileImageUrl;
}
