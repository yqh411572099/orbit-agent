package com.butler.application;

import com.butler.domain.model.User;
import com.butler.domain.model.UserType;
import com.butler.domain.repository.UserRepository;
import java.time.Instant;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户注册/登录。密码使用 BCrypt 哈希存储。 */
@Service
public class UserAppService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAppService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    public User register(String username, String rawPassword, String nickname, UserType userType) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("账号不能为空");
        if (rawPassword == null || rawPassword.length() < 4) throw new IllegalArgumentException("密码至少 4 位");
        if (userRepository.existsByUsername(username)) throw new IllegalArgumentException("账号已存在");
        User user = new User(null, username.trim(), passwordEncoder.encode(rawPassword),
                nickname == null || nickname.isBlank() ? username.trim() : nickname.trim(),
                userType == null ? UserType.NORMAL : userType, Instant.now());
        return userRepository.save(user);
    }

    public User login(String username, String rawPassword) {
        User user = userRepository.findByUsername(username == null ? "" : username.trim())
                .orElseThrow(() -> new IllegalArgumentException("账号或密码错误"));
        if (!passwordEncoder.matches(rawPassword == null ? "" : rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        return user;
    }

    public java.util.Optional<User> user(Long userId) {
        return userRepository.findById(userId);
    }

    public UserType typeOf(Long userId) {
        return userRepository.findById(userId).map(User::getUserType).orElse(UserType.NORMAL);
    }
}
