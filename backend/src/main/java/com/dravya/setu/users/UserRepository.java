package com.dravya.setu.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            update User u set u.emailVerified = true, u.emailVerifiedAt = :now, u.updatedAt = :now
            where u.id = :id and u.emailVerified = false
            """)
    int markEmailVerified(@org.springframework.data.repository.query.Param("id") Long id,
                          @org.springframework.data.repository.query.Param("now") java.time.OffsetDateTime now);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
