package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.Shelter;

//임시로 이름/주소로 검색
public interface ShelterSearchRepository extends JpaRepository<Shelter, Long> {

    // 이름으로 검색
    List<Shelter> findByNameContainingIgnoreCase(String name);

    // 주소로 검색
    List<Shelter> findByAddressContainingIgnoreCase(String address);
}