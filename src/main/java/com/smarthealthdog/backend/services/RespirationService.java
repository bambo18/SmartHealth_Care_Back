package com.smarthealthdog.backend.services;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smarthealthdog.backend.domain.Pet;
import com.smarthealthdog.backend.domain.RespirationRecord;
import com.smarthealthdog.backend.domain.RespirationStatus;
import com.smarthealthdog.backend.dto.respiration.CreateRespirationUploadRequest;
import com.smarthealthdog.backend.dto.respiration.RespirationRecordResponse;
import com.smarthealthdog.backend.dto.respiration.RespirationUploadUrlResponse;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.PetRepository;
import com.smarthealthdog.backend.repositories.RespirationRecordRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class RespirationService {
    private final PetRepository petRepository;
    private final RespirationRecordRepository respirationRecordRepository;
    private final RespirationVideoStorageService storageService;

    private Pet requireOwnedPet(Long petId, Long ownerId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!pet.getOwner().getId().equals(ownerId)) {
            // Avoid disclosing another user's pet existence.
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return pet;
    }

    private RespirationRecord requireOwnedRecord(Long petId, UUID videoId) {
        RespirationRecord record = respirationRecordRepository.findById(videoId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!record.getPet().getId().equals(petId)) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return record;
    }

    @Transactional
    public RespirationUploadUrlResponse prepareUpload(Long petId, Long ownerId,
                                                       CreateRespirationUploadRequest request) {
        Pet pet = requireOwnedPet(petId, ownerId);
        UUID videoId = UUID.randomUUID();
        String key = storageService.generateVideoKey(petId, videoId);
        String url = storageService.createUploadUrl(key);
        respirationRecordRepository.save(new RespirationRecord(
                videoId, pet, key, request.fileSizeBytes(), request.durationSec()));
        return new RespirationUploadUrlResponse(videoId, url, key, 900);
    }

    @Transactional
    public RespirationRecordResponse completeUpload(Long petId, UUID videoId, Long ownerId) {
        requireOwnedPet(petId, ownerId);
        RespirationRecord record = requireOwnedRecord(petId, videoId);

        // Repeat requests are safe: no duplicate work is emitted here.
        if (record.getStatus() == RespirationStatus.PENDING) {
            return RespirationRecordResponse.from(record);
        }
        if (record.getStatus() != RespirationStatus.UPLOADING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Upload cannot be completed in this status");
        }

        final long actualSize;
        try {
            actualSize = storageService.validateUploadedVideo(record.getStorageKey());
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Video not found in OVH S3 yet", ex);
            }
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        if (actualSize != record.getExpectedSizeBytes()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Uploaded video size differs from the size requested");
        }

        record.markUploaded(actualSize);
        return RespirationRecordResponse.from(record);
    }

    @Transactional(readOnly = true)
    public RespirationRecordResponse getOne(Long petId, UUID videoId, Long ownerId) {
        requireOwnedPet(petId, ownerId);
        return RespirationRecordResponse.from(requireOwnedRecord(petId, videoId));
    }

    @Transactional(readOnly = true)
    public List<RespirationRecordResponse> list(Long petId, Long ownerId) {
        requireOwnedPet(petId, ownerId);
        return respirationRecordRepository.findTop50ByPet_IdOrderByCreatedAtDesc(petId)
                .stream().map(RespirationRecordResponse::from).toList();
    }
}
