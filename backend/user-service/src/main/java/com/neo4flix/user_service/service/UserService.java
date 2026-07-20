package com.neo4flix.user_service.service;

import com.neo4flix.user_service.domain.User;
import com.neo4flix.user_service.dto.UserResponse;
import com.neo4flix.user_service.exception.UserNotFoundException;
import com.neo4flix.user_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getUserId(), user.getUsername(), user.getEmail(), user.getRoles(), user.getCreatedAt());
    }
}
