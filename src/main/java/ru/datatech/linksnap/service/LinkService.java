package ru.datatech.linksnap.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.datatech.linksnap.entity.Link;
import ru.datatech.linksnap.repository.LinkRepository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class LinkService {

    private final LinkRepository linkRepository;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${app.short-code.length:8}")
    private int shortCodeLength;

    @Value("${app.cache.redirect-ttl:3600}")
    private long redirectTtlSeconds;

    public LinkService(LinkRepository linkRepository, RedisTemplate<String, String> redisTemplate) {
        this.linkRepository = linkRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public String createShortLink(String originalUrl) {
        Optional<Link> existing = linkRepository.findByOriginalUrl(originalUrl);

        if (existing.isPresent()) {
            return existing.get().getShortCode();
        }

        String shortCode = generateShortCode();

        while (linkRepository.existsByShortCode(shortCode)) {
            shortCode = generateShortCode();
        }

        Link link = new Link();
        link.setShortCode(shortCode);
        link.setOriginalUrl(originalUrl);

        linkRepository.save(link);

        cacheLink(shortCode, originalUrl);

        return shortCode;
    }

    public String getOriginalUrl(String shortCode) {
        String cacheKey = "shortcode:" + shortCode;
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);

        if (cachedUrl != null) {
            return cachedUrl;
        }

        Link link = linkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("Ссылка не найдена"));
        cacheLink(shortCode, link.getOriginalUrl());

        return link.getOriginalUrl();
    }

    private void cacheLink(String shortCode, String originalUrl) {
        String cacheKey = "shortcode:" + shortCode;
        redisTemplate.opsForValue().set(cacheKey, originalUrl, redirectTtlSeconds, TimeUnit.SECONDS);
    }

    private String generateShortCode() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < shortCodeLength; i++) {
            int index = (int) (Math.random() * characters.length());
            sb.append(characters.charAt(index));
        }
        return sb.toString();
    }
}
