package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smarthealthdog.backend.domain.Shelter;


public interface ShelterSearchRepository
        extends JpaRepository<Shelter, Long> {


    // ============================================================
    // 활성화된 전체 보호소
    // ============================================================

    @Query(
            value = """
                    SELECT s.*
                    FROM shelters s
                    WHERE s.is_active = TRUE
                    ORDER BY s.id ASC
                    """,
            nativeQuery = true
    )
    List<Shelter> findAllActive();


    // ============================================================
    // 지역 검색
    // ============================================================
    //
    // province / district 우선
    //
    // 예:
    //
    // location = "서울"
    // → province = "서울특별시" 검색 가능
    //
    // location = "경기"
    // → province = "경기도" 검색 가능
    //
    // location = "고양"
    // → district = "고양시" 검색 가능
    //
    // 주소에도 동일 문자열이 들어있는 경우를 대비해
    // address도 함께 검색한다.
    //
    // 이름 검색도 기존 기능 호환을 위해 유지한다.
    //
    // ============================================================

    @Query(
            value = """
                    SELECT s.*
                    FROM shelters s
                    WHERE s.is_active = TRUE
                      AND (
                            COALESCE(s.province, '')
                                ILIKE '%' || :location || '%'

                            OR COALESCE(s.district, '')
                                ILIKE '%' || :location || '%'

                            OR COALESCE(s.address, '')
                                ILIKE '%' || :location || '%'

                            OR COALESCE(s.name, '')
                                ILIKE '%' || :location || '%'
                      )
                    ORDER BY s.id ASC
                    """,
            nativeQuery = true
    )
    List<Shelter> searchActiveByLocation(
            @Param("location")
            String location
    );
}