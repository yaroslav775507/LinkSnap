package ru.datatech.linksnap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.datatech.linksnap.entity.Link;

import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {

    Optional<Link> findByShortCode(String shortCode);

    boolean existsByShortCode(String shortCode);

    Optional<Link> findByOriginalUrl(String originalUrl);
}
