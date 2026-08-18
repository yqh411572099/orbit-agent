package com.butler.config;

import com.butler.application.UserAppService;
import com.butler.domain.model.UserType;
import com.butler.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 启动时确保存在固定测试账号 test（默认密码 test123，可用 butler.test-user.password 覆盖）。 */
@Component
public class UserBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    private final UserAppService userAppService;
    private final UserRepository userRepository;
    private final String testPassword;

    public UserBootstrap(UserAppService userAppService, UserRepository userRepository, Environment env) {
        this.userAppService = userAppService;
        this.userRepository = userRepository;
        this.testPassword = env.getProperty("butler.test-user.password", "test123");
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("test")) {
            userAppService.register("test", testPassword, "测试用户", UserType.TEST);
            log.info("已创建测试账号 test / {}", testPassword);
        }
    }
}
