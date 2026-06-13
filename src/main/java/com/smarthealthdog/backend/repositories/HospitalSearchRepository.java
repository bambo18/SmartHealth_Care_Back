package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smarthealthdog.backend.domain.Hospital;

//임시로 이름/주소로 검색
public interface HospitalSearchRepository extends JpaRepository<Hospital, Long> {

    // 이름으로 검색
    List<Hospital> findByNameContainingIgnoreCase(String name);

    // 주소로 검색
    List<Hospital> findByAddressContainingIgnoreCase(String address);
}