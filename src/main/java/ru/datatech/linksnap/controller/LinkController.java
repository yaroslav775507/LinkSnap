package ru.datatech.linksnap.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.datatech.linksnap.service.LinkService;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping("/links")
    public ResponseEntity<?> createLink(@RequestBody Map<String, String> request) {
        String originalUrl = request.get("url");

        if (originalUrl == null || originalUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "URL is required"));
        }

        String shortCode = linkService.createShortLink(originalUrl);

        return ResponseEntity.ok(Map.of(
                "shortCode", shortCode,
                "shortUrl", "http://localhost:8080/r/" + shortCode,
                "originalUrl", originalUrl
        ));
    }

    @GetMapping("/r/{shortCode}")
    public ResponseEntity<Void> redirectOriginalLink(@PathVariable String shortCode) {
        String originalUrl = linkService.getOriginalUrl(shortCode);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", originalUrl)
                .build();
    }
}
