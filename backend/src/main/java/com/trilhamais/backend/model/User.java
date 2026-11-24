package com.trilhamais.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

/**
 * Entidade JPA que representa a tabela de usuarios (estudantes) no banco de dados.
 * Usamos o nome de tabela "users" porque "user" e uma palavra reservada em muitos bancos SQL, incluindo MySQL.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Column(nullable = false)
    private String password;

    @Column(name = "streak_days", nullable = false)
    private int streakDays = 0;

    @Column(name = "avatar_emoji", length = 20)
    private String avatarEmoji = "🎓";

    // Chave de recuperacao de senha (formato TRLH-XXXX-XXXX-XXXX). So o hash BCrypt fica no banco.
    @Column(name = "recovery_key_hash")
    private String recoveryKeyHash;

    @Column(name = "recovery_key_created_at")
    private java.time.LocalDateTime recoveryKeyCreatedAt;

    // LGPD/ECA: quando e qual versao dos Termos de Uso e da Politica de Privacidade o estudante aceitou
    @Column(name = "terms_accepted_at")
    private java.time.LocalDateTime termsAcceptedAt;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Subject> subjects = new java.util.ArrayList<>();

    // Construtor padrao sem argumentos (obrigatorio para a especificacao JPA/Hibernate)
    public User() {
    }

    // Construtor de conveniencia para criacao de novos usuarios
    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    // Getters e Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getStreakDays() {
        return streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = streakDays;
    }

    public String getAvatarEmoji() {
        return avatarEmoji;
    }

    public void setAvatarEmoji(String avatarEmoji) {
        this.avatarEmoji = avatarEmoji;
    }

    public String getRecoveryKeyHash() {
        return recoveryKeyHash;
    }

    public void setRecoveryKeyHash(String recoveryKeyHash) {
        this.recoveryKeyHash = recoveryKeyHash;
    }

    public java.time.LocalDateTime getRecoveryKeyCreatedAt() {
        return recoveryKeyCreatedAt;
    }

    public void setRecoveryKeyCreatedAt(java.time.LocalDateTime recoveryKeyCreatedAt) {
        this.recoveryKeyCreatedAt = recoveryKeyCreatedAt;
    }

    public java.time.LocalDateTime getTermsAcceptedAt() {
        return termsAcceptedAt;
    }

    public void setTermsAcceptedAt(java.time.LocalDateTime termsAcceptedAt) {
        this.termsAcceptedAt = termsAcceptedAt;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public void setTermsVersion(String termsVersion) {
        this.termsVersion = termsVersion;
    }

    public java.util.List<Subject> getSubjects() {
        return subjects;
    }

    public void setSubjects(java.util.List<Subject> subjects) {
        this.subjects = subjects;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id) || Objects.equals(email, user.email);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
