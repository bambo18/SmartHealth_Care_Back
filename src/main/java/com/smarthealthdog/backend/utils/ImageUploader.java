package com.smarthealthdog.backend.utils;

import java.io.IOException;
import com.smarthealthdog.backend.dto.diagnosis.create.SubmissionImageUploadEvent;
import com.smarthealthdog.backend.dto.pets.PetPictureUploadEvent;
import com.smarthealthdog.backend.dto.users.UserProfilePictureUploadEvent;

public interface ImageUploader {

    void uploadProfilePicture(
        UserProfilePictureUploadEvent event
    ) throws IOException;

    void uploadPetImage(
        PetPictureUploadEvent event
    ) throws IOException;

    void uploadSubmissionImage(
        SubmissionImageUploadEvent event
    ) throws IOException;
}