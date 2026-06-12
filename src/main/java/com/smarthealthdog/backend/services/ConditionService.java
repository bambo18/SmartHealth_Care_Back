package com.smarthealthdog.backend.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.smarthealthdog.backend.domain.Condition;
import com.smarthealthdog.backend.domain.ConditionTranslation;
import com.smarthealthdog.backend.domain.Language;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.repositories.ConditionRepository;
import com.smarthealthdog.backend.repositories.ConditionTranslationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConditionService {

    private final ConditionRepository conditionRepository;
    private final ConditionTranslationRepository conditionTranslationRepository;

    /**
     * 주어진 반려동물 종(species)에 해당하는 모든 질병(Condition) 정보를 반환합니다.
     * @param species 반려동물 종
     * @return 해당 종에 대한 질병 목록
     */
    public List<Condition> getConditionsBySpecies(PetSpecies species) {
        if (species == null) {
            throw new IllegalArgumentException("종(species)은 null일 수 없습니다.");
        }

        List<Condition> conditions = conditionRepository.findBySpecies(species);
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("해당 종에 대한 질병 정보가 없습니다.");
        }

        return conditions;
    }

    /**
     * 주어진 조건 ID 목록과 선호 언어에 해당하는 ConditionTranslation 목록을 반환합니다.
     * @param conditionIds 조건 ID 목록
     * @param preferredLanguage 선호 언어
     * @return 해당 조건 ID와 언어에 대한 번역 목록
     */
    public List<ConditionTranslation> getConditionTranslationsByConditionIdsAndLanguage(
            List<Integer> conditionIds, 
            Language preferredLanguage
    ) {
        return conditionTranslationRepository.findByConditionIdsAndLanguage(conditionIds, preferredLanguage.getId());
    }
}
