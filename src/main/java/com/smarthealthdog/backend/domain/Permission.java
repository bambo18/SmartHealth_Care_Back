package com.smarthealthdog.backend.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Convert(converter = PermissionConverter.class)
    @Column(name = "name", nullable = false, length = 255, unique = true)
    private PermissionEnum name;

    @Column(name = "description", length = 1024)
    private String description;

    @ManyToMany(mappedBy = "permissions")
    private Set<Role> roles;
}