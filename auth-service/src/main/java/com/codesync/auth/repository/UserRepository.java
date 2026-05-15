package com.codesync.auth.repository;

import com.codesync.auth.entity.User;
import com.codesync.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsernameIgnoreCase(String username);
    Optional<User> findByUserId(Long userId);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
    List<User> findAllByRole(UserRole role);
    void deleteByUserId(Long userId);

    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    List<User> searchByUsername(@Param("keyword") String keyword);
}
