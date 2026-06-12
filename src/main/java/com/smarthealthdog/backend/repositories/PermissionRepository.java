package com.smarthealthdog.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.Permission;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Integer> {
}
