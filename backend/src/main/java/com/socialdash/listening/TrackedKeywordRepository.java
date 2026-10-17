package com.socialdash.listening;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackedKeywordRepository extends JpaRepository<TrackedKeyword, Long> {

    List<TrackedKeyword> findByUserIdOrderByCreatedAt(Long userId);

    Optional<TrackedKeyword> findByIdAndUserId(Long id, Long userId);

    List<TrackedKeyword> findByUserIdAndKeywordIgnoreCase(Long userId, String keyword);
}
