package com.ichi.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ichi.user.domain.User;

public interface UserRepository extends JpaRepository<User, String> {
}
