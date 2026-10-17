package com.socialdash.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    List<SocialAccount> findByUserIdOrderByConnectedAt(Long userId);

    List<SocialAccount> findByUserIdAndPlatform(Long userId, Platform platform);

    Optional<SocialAccount> findByIdAndUserId(Long id, Long userId);

    List<SocialAccount> findByIdInAndUserId(List<Long> ids, Long userId);

    boolean existsByUserIdAndPlatformAndHandleIgnoreCase(Long userId, Platform platform, String handle);
}
