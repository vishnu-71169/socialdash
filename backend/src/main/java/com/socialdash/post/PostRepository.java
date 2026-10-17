package com.socialdash.post;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    Optional<Post> findByIdAndUserId(Long id, Long userId);

    Page<Post> findByUserId(Long userId, Pageable pageable);

    Page<Post> findByUserIdAndStatusIn(Long userId, Collection<PostStatus> statuses, Pageable pageable);

    @Query("select p.status as status, count(p) as total from Post p where p.user.id = :userId group by p.status")
    List<StatusCount> countByStatus(Long userId);

    interface StatusCount {
        PostStatus getStatus();
        long getTotal();
    }
}
