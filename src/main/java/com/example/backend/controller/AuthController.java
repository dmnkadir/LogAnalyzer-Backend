package com.example.backend.controller;

import com.auth0.jwt.algorithms.Algorithm;
import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.auth0.jwt.JWT;

import java.util.Date;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Şifreyi kodun içine yazmak yerine application.properties'den çekiyoruz
    @Value("${jwt.secret}")
    private String SECRET_KEY;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public String register(@RequestBody User user) {
        String hashedPassword = passwordEncoder.encode(user.getPassword());
        user.setPassword(hashedPassword);
        userRepository.save(user);
        return "Harika! Kullanıcı başarıyla kaydedildi.";
    }

    @PostMapping("/login")
    public String login(@RequestBody User loginRequest) {
        Optional<User> userOptional = userRepository.findByUsername(loginRequest.getUsername());

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {

                Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
                String token = JWT.create()
                        .withSubject(user.getUsername())
                        .withIssuedAt(new Date())
                        .withExpiresAt(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000)) // 1 gün geçerli
                        .sign(algorithm);

                return token;
            }
        }
        return "Hata: Kullanıcı adı veya şifre yanlış!";
    }
}