package com.neo4flix.user_service.web;

import com.neo4flix.user_service.dto.UserResponse;
import com.neo4flix.user_service.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return userService.getById(principal.getName());
    }
}
