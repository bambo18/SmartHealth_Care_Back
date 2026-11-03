package com.smarthealthdog.backend.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.domain.HealthCheckAnswer;
import com.smarthealthdog.backend.domain.HealthCheckArea;
import com.smarthealthdog.backend.domain.HealthCheckRecord;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetSpecies;
import com.smarthealthdog.backend.dto.healthcheck.HealthCheckCreateRequest;
import com.smarthealthdog.backend.dto.healthcheck.HealthCheckResponse;
import com.smarthealthdog.backend.dto.healthcheck.HealthCheckSummaryResponse;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.HealthCheckAnswerRepository;
import com.smarthealthdog.backend.repositories.HealthCheckAreaRepository;
import com.smarthealthdog.backend.repositories.HealthCheckRecordRepository;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HealthCheckService {
    private final PetRepository petRepository;
    private final HealthCheckRecordRepository recordRepository;
    private final HealthCheckAreaRepository areaRepository;
    private final HealthCheckAnswerRepository answerRepository;

    private Pet ownedPet(Long petId, Long userId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!pet.getOwner().getId().equals(userId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return pet;
    }

    @Transactional
    public HealthCheckResponse create(Long petId, Long userId, HealthCheckCreateRequest request) {
        Pet pet = ownedPet(petId, userId);
        if (request == null || request.species() == null || request.answers() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "species and answers are required");
        }
        PetSpecies species;
        try {
            species = PetSpecies.valueOf(request.species().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid species (dog or cat)");
        }
        if (pet.getSpecies() != species) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Species does not match the pet");
        }

        List<HealthCheckCatalog.Area> catalog = HealthCheckCatalog.AREAS.get(species);
        Map<String, List<Integer>> supplied = request.answers();
        if (supplied.size() != catalog.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exactly five areas are required");
        }
        Set<String> keys = new HashSet<>();
        for (HealthCheckCatalog.Area area : catalog) keys.add(area.key());
        if (!supplied.keySet().equals(keys)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Area keys do not match the checklist");
        }

        record Calculated(HealthCheckCatalog.Area area, List<Integer> values,
                          int score, int stage, boolean urgent) {}
        List<Calculated> calculated = new ArrayList<>();
        int overallStage = 1;
        for (HealthCheckCatalog.Area area : catalog) {
            List<Integer> values = supplied.get(area.key());
            if (values == null || values.size() != area.questions().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect answer count for " + area.key());
            }
            int sum = 0, max = 0;
            boolean urgent = false;
            for (int i = 0; i < area.questions().size(); i++) {
                Integer value = values.get(i);
                if (value == null || value < 0 || value > 3) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer must be 0..3 at " + area.key() + ":" + i);
                }
                String weight = area.questions().get(i).weight();
                if ("urgent".equals(weight)) {
                    if (value > 0) urgent = true;
                } else {
                    int w = Integer.parseInt(weight);
                    sum += w * value;
                    max += w * 3;
                }
            }
            int score = max > 0 ? (int) Math.round(sum * 100.0 / max) : 0;
            int stage = urgent ? 4 : score >= 40 ? 3 : score >= 20 ? 2 : 1;
            overallStage = Math.max(overallStage, stage);
            calculated.add(new Calculated(area, values, score, stage, urgent));
        }

        HealthCheckRecord record = recordRepository.save(HealthCheckRecord.builder()
                .pet(pet).species(species).overallStage((short) overallStage)
                .scoringVersion(HealthCheckCatalog.VERSION).build());
        for (Calculated c : calculated) {
            HealthCheckArea savedArea = areaRepository.save(HealthCheckArea.builder()
                    .record(record).areaKey(c.area().key()).areaTitle(c.area().title())
                    .score(c.score()).stage((short) c.stage()).urgentHit(c.urgent()).build());
            List<HealthCheckAnswer> answerEntities = new ArrayList<>();
            for (int i = 0; i < c.values().size(); i++) {
                HealthCheckCatalog.Question q = c.area().questions().get(i);
                answerEntities.add(HealthCheckAnswer.builder()
                        .area(savedArea).questionIndex(i).questionText(q.text())
                        .questionWeight(q.weight()).answerValue(c.values().get(i).shortValue()).build());
            }
            answerRepository.saveAll(answerEntities);
        }
        // Force inserts (and DB defaults) before building response.
        recordRepository.flush();
        return detail(petId, userId, record.getId());
    }

    @Transactional(readOnly = true)
    public Page<HealthCheckSummaryResponse> list(Long petId, Long userId, int page, int size) {
        ownedPet(petId, userId);
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page >= 0 and size 1..100 required");
        }
        return recordRepository.findByPetId(petId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(this::summary);
    }

    @Transactional(readOnly = true)
    public HealthCheckResponse detail(Long petId, Long userId, Long recordId) {
        ownedPet(petId, userId);
        HealthCheckRecord record = recordRepository.findByIdAndPetId(recordId, petId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        List<HealthCheckResponse.AreaResult> areaResults = areaRepository.findByRecordId(recordId)
                .stream().map(area -> new HealthCheckResponse.AreaResult(
                        area.getAreaKey(), area.getAreaTitle(), area.getScore(),
                        area.getStage().intValue(), area.getUrgentHit(),
                        answerRepository.findByAreaIdOrderByQuestionIndexAsc(area.getId()).stream()
                                .map(a -> new HealthCheckResponse.AnswerResult(
                                        a.getQuestionIndex(), a.getQuestionText(),
                                        a.getQuestionWeight(), a.getAnswerValue().intValue()))
                                .toList()))
                .toList();
        return new HealthCheckResponse(record.getId(), petId,
                record.getSpecies().getValue(), record.getOverallStage().intValue(),
                record.getScoringVersion(), record.getCreatedAt(), areaResults);
    }

    @Transactional
    public void delete(Long petId, Long userId, Long recordId) {
        ownedPet(petId, userId);
        HealthCheckRecord record = recordRepository.findByIdAndPetId(recordId, petId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        recordRepository.delete(record);
    }

    private HealthCheckSummaryResponse summary(HealthCheckRecord record) {
        return new HealthCheckSummaryResponse(record.getId(), record.getPet().getId(),
                record.getSpecies().getValue(), record.getOverallStage().intValue(),
                record.getScoringVersion(), record.getCreatedAt());
    }
}
