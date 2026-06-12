package com.smarthealthdog.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.smarthealthdog.backend.domain.ConditionTranslation;

@Repository
public interface ConditionTranslationRepository extends JpaRepository<ConditionTranslation, Integer> {
    @Query("SELECT ct FROM ConditionTranslation ct WHERE ct.condition.id IN :conditionIds AND ct.languageId = :languageId")
    List<ConditionTranslation> findByConditionIdsAndLanguage(
        @Param("conditionIds") 
        List<Integer> conditionIds, 
        @Param("languageId") Integer languageId
    );
}
