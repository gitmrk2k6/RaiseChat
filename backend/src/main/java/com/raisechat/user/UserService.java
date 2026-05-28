package com.raisechat.user;

import com.raisechat.user.dto.UpdateMeRequest;
import com.raisechat.user.dto.UserResponse;
import com.raisechat.user.exception.UserNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse updateMe(Long userId, UpdateMeRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (req.displayName() != null) {
            user.setDisplayName(req.displayName());
        }
        if (req.statusMessage() != null) {
            user.setStatusMessage(req.statusMessage());
        }

        return UserResponse.from(user);
    }
}
