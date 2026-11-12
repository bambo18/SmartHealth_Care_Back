package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smarthealthdog.backend.domain.Shelter;


public interface ShelterSearchRepository
        extends JpaRepository<Shelter, Long> {


    // ============================================================
    // 전체 활성 보호소
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
    // location 문자열 직접 검색
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


    // ============================================================
    // 시·도 검색
    // ============================================================

    @Query(
            value = """
                    SELECT s.*
                    FROM shelters s
                    WHERE s.is_active = TRUE
                      AND (
                            COALESCE(s.province, '')
                                ILIKE '%' || :province || '%'

                            OR COALESCE(s.jurisdiction, '')
                                ILIKE '%' || :province || '%'
                      )
                    ORDER BY s.id ASC
                    """,
            nativeQuery = true
    )
    List<Shelter> searchActiveByProvince(
            @Param("province")
            String province
    );


    // ============================================================
    // 시·군·구 검색
    // ============================================================

    @Query(
            value = """
                    SELECT s.*
                    FROM shelters s
                    WHERE s.is_active = TRUE
                      AND (
                            COALESCE(s.district, '')
                                ILIKE '%' || :district || '%'

                            OR COALESCE(s.jurisdiction, '')
                                ILIKE '%' || :district || '%'
                      )
                    ORDER BY s.id ASC
                    """,
            nativeQuery = true
    )
    List<Shelter> searchActiveByDistrict(
            @Param("district")
            String district
    );


    // ============================================================
    // 시·도 + 시·군·구 검색
    // ============================================================
    //
    // 예:
    //
    // province = 경기도
    // district = 파주시
    //
    // 실제 소재지:
    // 경기도 양주시
    //
    // 관할:
    // 경기도 파주시
    //
    // 이런 관외 위탁 보호소도 jurisdiction으로 검색된다.
    //
    // ============================================================

    @Query(
            value = """
                    SELECT s.*
                    FROM shelters s
                    WHERE s.is_active = TRUE
                      AND (
                            (
                                COALESCE(s.province, '')
                                    ILIKE '%' || :province || '%'

                                OR COALESCE(s.jurisdiction, '')
                                    ILIKE '%' || :province || '%'
                            )
                            AND
                            (
                                COALESCE(s.district, '')
                                    ILIKE '%' || :district || '%'

                                OR COALESCE(s.jurisdiction, '')
                                    ILIKE '%' || :district || '%'
                            )
                      )
                    ORDER BY s.id ASC
                    """,
            nativeQuery = true
    )
    List<Shelter> searchActiveByAdministrativeRegion(

            @Param("province")
            String province,

            @Param("district")
            String district
    );
}