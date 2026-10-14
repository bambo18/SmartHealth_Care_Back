package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smarthealthdog.backend.domain.Shelter;


public interface ShelterSearchRepository
        extends JpaRepository<Shelter, Long> {


    // ============================================================
    // 활성화된 전체 보호소 조회
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
    // 지역별 보호소 검색
    // ============================================================
    //
    // 검색 대상:
    //
    // 1. province
    //    예: 서울 → 서울특별시
    //        경기 → 경기도
    //
    // 2. district
    //    예: 고양 → 고양시
    //        마포 → 마포구
    //
    // 3. jurisdiction
    //    실제 시설 소재지와 관할지역이 다른
    //    위탁 보호소 검색을 위해 사용
    //
    //    예:
    //    한국동물구조관리협회
    //    실제 소재지 = 경기도 양주시
    //    관할지역 = 강남구, 서초구, 양천구, 파주시 등
    //
    // 4. address
    //
    // 5. name
    //
    // is_active = TRUE인 시설만 반환
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

                            OR COALESCE(s.jurisdiction, '')
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