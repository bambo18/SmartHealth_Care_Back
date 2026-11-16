package com.smarthealthdog.backend.services;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smarthealthdog.backend.clients.ocr.HealthCertificateFieldExtractor;
import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.PetHealthCertificate;
import com.smarthealthdog.backend.domain.Submission;
import com.smarthealthdog.backend.domain.SubmissionStatus;
import com.smarthealthdog.backend.domain.SubmissionTypeEnum;
import com.smarthealthdog.backend.dto.health.ManualHealthCertificateRequest;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.PetHealthCertificateRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ManualHealthCertificateService {
    private final PetService petService;
    private final SubmissionService submissionService;
    private final PetHealthCertificateRepository petHealthCertificateRepository;
    private final HealthCertificateFieldExtractor fieldExtractor;

    /** OCR 워커나 S3 업로드 이벤트를 발생시키지 않는 신규 수기 등록. */
    @Transactional
    public Map<String, Object> create(Long petId, Long ownerId, ManualHealthCertificateRequest request) {
        requireNonemptyManualData(request);
        Pet pet = petService.get(petId);
        if (!pet.getOwner().getId().equals(ownerId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        // 먼저 COMPLETED로 만들어 저장. PENDING 검색/큐 발행 대상에 들어가지 않도록 한다.
        Submission submission = submissionService.createSubmission(pet, SubmissionTypeEnum.HEALTH_CERTIFICATE);
        submission.setStatus(SubmissionStatus.COMPLETED);
        submission.setCompletedAt(Instant.now());
        submission.setFailureReason(null);
        submission = submissionService.saveSubmission(submission);

        PetHealthCertificate certificate = new PetHealthCertificate();
        certificate.setPet(pet);
        certificate.setSubmission(submission);
        certificate.setOcrConfidence(null);
        certificate.setManuallyEdited(true);
        apply(request, certificate);
        certificate = petHealthCertificateRepository.save(certificate);

        return response(submission, certificate);
    }

    /** OCR 완료 후 누락/오인식 항목 수정. OCR 처리 중에는 수정하지 않음. */
    @Transactional
    public Map<String, Object> updateFields(UUID submissionId, Long ownerId,
                                            ManualHealthCertificateRequest request) {
        requireData(request);
        Submission submission = requireCompletedCertificateSubmission(submissionId, ownerId);
        PetHealthCertificate certificate = petHealthCertificateRepository
                .findBySubmissionIdWithPet(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND));

        apply(request, certificate);
        certificate.setManuallyEdited(true);
        certificate = petHealthCertificateRepository.save(certificate);
        return response(submission, certificate);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFields(UUID submissionId, Long ownerId) {
        Submission submission = requireCompletedCertificateSubmission(submissionId, ownerId);
        PetHealthCertificate certificate = petHealthCertificateRepository
                .findBySubmissionIdWithPet(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND));
        return response(submission, certificate);
    }

    private Submission requireCompletedCertificateSubmission(UUID submissionId, Long ownerId) {
        Submission submission = submissionService.getSubmissionByIdAndOwnerId(submissionId, ownerId);
        if (submission.getType() != SubmissionTypeEnum.HEALTH_CERTIFICATE) {
            throw new ResourceNotFoundException(ErrorCode.HEALTH_CERTIFICATE_NOT_FOUND);
        }
        if (submission.getStatus() != SubmissionStatus.COMPLETED) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }
        return submission;
    }

    private void requireNonemptyManualData(ManualHealthCertificateRequest r) {
        if (r == null || !hasAnyNonblankField(r)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean hasAnyNonblankField(ManualHealthCertificateRequest r) {
        return nonblank(r.getHospitalName()) || nonblank(r.getAnimalName()) || nonblank(r.getSpeciesText())
                || nonblank(r.getBreed()) || nonblank(r.getGenderText())
                || nonblank(r.getCoatColor()) || nonblank(r.getAgeText())
                || nonblank(r.getFeatures()) || nonblank(r.getRegistrationNumber())
                || nonblank(r.getDiseaseName()) || nonblank(r.getOnsetDateText())
                || nonblank(r.getDiagnosedDateText()) || nonblank(r.getSymptoms())
                || nonblank(r.getTreatment()) || nonblank(r.getHospitalizationDischargeDateText())
                || nonblank(r.getDocumentDateText()) || nonblank(r.getPrognosis())
                || nonblank(r.getRemarks());
    }

    private boolean nonblank(String s) {
        return s != null && !s.isBlank();
    }

    private void requireData(ManualHealthCertificateRequest r) {
        if (r == null || !hasAnyField(r)) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
        }
    }

    private boolean hasAnyField(ManualHealthCertificateRequest r) {
        return hasValue(r.getHospitalName()) || hasValue(r.getAnimalName()) || hasValue(r.getSpeciesText())
                || hasValue(r.getBreed()) || hasValue(r.getGenderText())
                || hasValue(r.getCoatColor()) || hasValue(r.getAgeText())
                || hasValue(r.getFeatures()) || hasValue(r.getRegistrationNumber())
                || hasValue(r.getDiseaseName()) || hasValue(r.getOnsetDateText())
                || hasValue(r.getDiagnosedDateText()) || hasValue(r.getSymptoms())
                || hasValue(r.getTreatment()) || hasValue(r.getHospitalizationDischargeDateText())
                || hasValue(r.getDocumentDateText()) || hasValue(r.getPrognosis())
                || hasValue(r.getRemarks());
    }

    private boolean hasValue(String text) {
        return text != null; // PATCH에서 "" 는 해당 필드를 비우려는 유효한 요청
    }

    /** 필드가 null/생략이면 변경하지 않고, 빈 문자열은 DB NULL로 정리한다. */
    private void apply(ManualHealthCertificateRequest r, PetHealthCertificate c) {
        if (r.getHospitalName() != null) {
            String name = normalized(r.getHospitalName());
            if (name != null && name.length() > 255) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_INPUT);
            }
            c.setHospitalName(name);
        }
        applyString(r.getAnimalName(), c::setAnimalName);
        applyString(r.getSpeciesText(), c::setSpeciesText);
        applyString(r.getBreed(), c::setBreed);
        applyString(r.getGenderText(), c::setGenderText);
        applyString(r.getCoatColor(), c::setCoatColor);
        applyString(r.getAgeText(), c::setAgeText);
        applyString(r.getFeatures(), c::setFeatures);
        applyString(r.getRegistrationNumber(), c::setRegistrationNumber);
        applyString(r.getDiseaseName(), c::setDiseaseName);
        applyString(r.getSymptoms(), c::setSymptoms);
        applyString(r.getTreatment(), c::setTreatment);
        applyString(r.getHospitalizationDischargeDateText(), c::setHospitalizationDischargeDateText);
        applyString(r.getDocumentDateText(), c::setDocumentDateText);
        applyString(r.getPrognosis(), c::setPrognosis);
        applyString(r.getRemarks(), c::setRemarks);

        if (r.getOnsetDateText() != null) {
            String text = normalized(r.getOnsetDateText());
            c.setOnsetDateText(text);
            c.setOnsetDate(fieldExtractor.parseDate(text));
        }
        if (r.getDiagnosedDateText() != null) {
            String text = normalized(r.getDiagnosedDateText());
            c.setDiagnosedDateText(text);
            c.setDiagnosedDate(fieldExtractor.parseDate(text));
        }
    }

    private void applyString(String text, Consumer<String> setter) {
        if (text != null) setter.accept(normalized(text));
    }

    private String normalized(String value) {
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> response(Submission s, PetHealthCertificate c) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("submissionId", s.getId());
        map.put("petId", s.getPet().getId());
        map.put("status", s.getStatus());
        map.put("certificateId", c.getId());
        map.put("hospitalName", c.getHospitalName());
        map.put("animalName", c.getAnimalName());
        map.put("speciesText", c.getSpeciesText());
        map.put("breed", c.getBreed());
        map.put("genderText", c.getGenderText());
        map.put("coatColor", c.getCoatColor());
        map.put("ageText", c.getAgeText());
        map.put("features", c.getFeatures());
        map.put("registrationNumber", c.getRegistrationNumber());
        map.put("diseaseName", c.getDiseaseName());
        map.put("onsetDateText", c.getOnsetDateText());
        map.put("diagnosedDateText", c.getDiagnosedDateText());
        map.put("symptoms", c.getSymptoms());
        map.put("treatment", c.getTreatment());
        map.put("hospitalizationDischargeDateText", c.getHospitalizationDischargeDateText());
        map.put("documentDateText", c.getDocumentDateText());
        map.put("prognosis", c.getPrognosis());
        map.put("remarks", c.getRemarks());
        map.put("ocrConfidence", c.getOcrConfidence());
        map.put("manuallyEdited", c.isManuallyEdited());
        map.put("createdAt", c.getCreatedAt());
        map.put("updatedAt", c.getUpdatedAt());
        return map;
    }
}
