package chungbuk.cityfarmerplus.auth.repository;

import chungbuk.cityfarmerplus.auth.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginIdIgnoreCase(String loginId);

    boolean existsByLoginIdIgnoreCase(String loginId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from User user where user.id in :userIds order by user.id")
    List<User> findAllByIdForUpdate(@Param("userIds") Collection<Long> userIds);

    long countByUserType(User.UserType userType);

    long countByUserTypeAndAccountStatus(
            User.UserType userType,
            User.AccountStatus accountStatus
    );
}
