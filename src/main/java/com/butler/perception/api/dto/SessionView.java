package com.butler.perception.api.dto;

import com.butler.perception.api.UserController.UserView;

public record SessionView(String token, String expiresAt, UserView user) {}
