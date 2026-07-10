package com.example.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    @PostMapping("/upload")
    public ResponseEntity<String> uploadLogFile(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Hata: Lütfen bir dosya seçin!");
        }

        try {
            int lineCount = 0;
            int errorCount = 0;
            int infoCount = 0;
            int warnCount = 0;

            // 1. Dosyayı hafızaya alıp satır satır okumaya başlıyoruz
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                // 2. Dosyanın sonuna gelene kadar her satırı dön
                while ((line = br.readLine()) != null) {
                    lineCount++;

                    // 3. Satırın içinde geçen kelimelere göre sayaçları artır
                    if (line.contains("ERROR")) {
                        errorCount++;
                    } else if (line.contains("INFO")) {
                        infoCount++;
                    } else if (line.contains("WARN")) {
                        warnCount++;
                    }
                }
            }

            // 4. Sonuçları güzel bir metin haline getirip frontend'e (veya teste) yolla
            String result = String.format(
                    "Analiz Tamamlandı!\nToplam Satır: %d\nINFO Sayısı: %d\nWARN Sayısı: %d\nERROR Sayısı: %d",
                    lineCount, infoCount, warnCount, errorCount
            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Dosya okunurken hata oluştu: " + e.getMessage());
        }
    }
}