package com.smarthealthdog.backend.services;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.smarthealthdog.backend.domain.Role;
import com.smarthealthdog.backend.domain.SocialLoginProvider;
import com.smarthealthdog.backend.domain.SocialLoginUser;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.dto.UpdateUserProfileRequest;
import com.smarthealthdog.backend.dto.UserProfile;
import com.smarthealthdog.backend.exceptions.InternalServerErrorException;
import com.smarthealthdog.backend.exceptions.InvalidRequestDataException;
import com.smarthealthdog.backend.exceptions.ResourceNotFoundException;
import com.smarthealthdog.backend.repositories.SocialLoginUserRepository;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.utils.ImgUtils;
import com.smarthealthdog.backend.validation.ErrorCode;
import com.smarthealthdog.backend.validation.NicknameValidator;
import com.smarthealthdog.backend.validation.PasswordValidator;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UserService {

    private final ImgUtils imgUtils;
    private final UserRepository userRepository;
    private final SocialLoginProviderService socialLoginProviderService;
    private final SocialLoginUserRepository socialLoginUserRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final NicknameValidator nicknameValidator;
    private final FileUploadService fileUploadService;

    /**
     * 사용자 비밀번호 확인
     *
     * @param user 사용자
     * @param rawPassword 입력받은 비밀번호
     * @return 비밀번호가 일치하면 true
     */
    public boolean checkUserPassword(
        User user,
        String rawPassword
    ) {
        return passwordEncoder.matches(
            rawPassword,
            user.getPassword()
        );
    }

    /**
     * 카카오 소셜 로그인으로 새로운 사용자를 생성합니다.
     *
     * 카카오 동의항목에 따라 이메일이나 프로필 이미지가
     * 응답에 없을 수 있으므로 안전하게 처리합니다.
     *
     * @param kakaoUserInfo 카카오에서 제공하는 사용자 정보 JSON
     * @return 생성된 사용자
     */
    @Transactional
    public User createUserWithKakaoUserInfo(
        JsonNode kakaoUserInfo
    ) {
        if (kakaoUserInfo == null || kakaoUserInfo.isEmpty()) {
            throw new IllegalArgumentException(
                "카카오 사용자 정보가 유효하지 않습니다."
            );
        }

        String providerUserId =
            kakaoUserInfo.path("id").asText("");

        if (providerUserId.isBlank()) {
            throw new InternalServerErrorException(
                ErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        JsonNode kakaoAccount =
            kakaoUserInfo.path("kakao_account");

        if (
            kakaoAccount.isMissingNode() ||
            kakaoAccount.isNull()
        ) {
            throw new InternalServerErrorException(
                ErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        /*
         * 카카오계정 이메일은 사용자가 동의하지 않았거나
         * 앱에 이메일 조회 권한이 없으면 응답에 포함되지 않습니다.
         */
        String email =
            kakaoAccount.path("email").asText("");

        if (email.isBlank()) {
            email =
                "kakao_" +
                UUID.randomUUID()
                    .toString()
                    .replace("-", "") +
                "@noemail.smarthealthdog.com";
        }

        JsonNode profile =
            kakaoAccount.path("profile");

        /*
         * 카카오 닉네임이 없거나 서비스 닉네임 규칙에 맞지 않으면
         * UUID를 임시 닉네임으로 사용합니다.
         */
        String nickname =
            profile.path("nickname").asText("");

        if (
            nickname.isBlank() ||
            !nicknameValidator.isValid(nickname)
        ) {
            nickname = UUID.randomUUID().toString();
        }

        /*
         * 프로필 사진은 선택 동의항목이므로 없을 수 있습니다.
         */
        String profilePictureUrl =
            profile.path("profile_image_url").asText("");

        if (profilePictureUrl.isBlank()) {
            profilePictureUrl = null;
        }

        Role userRole =
            roleService.getSocialUserRole();

        /*
         * 카카오 소셜 로그인 사용자는 일반 로그인 비밀번호가 없으므로
         * password를 null로 저장합니다.
         */
        User newUser = User.builder()
            .nickname(nickname)
            .email(email)
            .password(null)
            .profilePic(profilePictureUrl)
            .role(userRole)
            .build();

        userRepository.save(newUser);

        SocialLoginProvider kakaoProvider =
            socialLoginProviderService.getKakaoProvider();

        SocialLoginUser socialLoginUser =
            SocialLoginUser.builder()
                .user(newUser)
                .provider(kakaoProvider)
                .providerUserId(providerUserId)
                .extraData(kakaoUserInfo.toString())
                .build();

        socialLoginUserRepository.save(socialLoginUser);

        return newUser;
    }

    /**
     * 기존 카카오 로그인 사용자의 정보를 업데이트합니다.
     *
     * 카카오 동의항목에 따라 닉네임, 이메일,
     * 프로필 사진이 응답에 없을 수 있습니다.
     *
     * 응답에 포함된 값만 업데이트하며,
     * 값이 없으면 기존 사용자 정보를 유지합니다.
     *
     * @param existingUser 기존 사용자
     * @param kakaoSocialLoginUser 카카오 소셜 로그인 연결 정보
     * @param kakaoUserInfo 카카오 사용자 정보
     * @return 업데이트된 사용자
     */
    @Transactional
    public User updateUserWithKakaoUserInfo(
        User existingUser,
        SocialLoginUser kakaoSocialLoginUser,
        JsonNode kakaoUserInfo
    ) {
        if (existingUser == null) {
            throw new InternalServerErrorException(
                ErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        if (kakaoSocialLoginUser == null) {
            throw new InternalServerErrorException(
                ErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        if (kakaoUserInfo == null || kakaoUserInfo.isEmpty()) {
            throw new InternalServerErrorException(
                ErrorCode.INTERNAL_SERVER_ERROR
            );
        }

        JsonNode kakaoAccount =
            kakaoUserInfo.path("kakao_account");

        JsonNode profile =
            kakaoAccount.path("profile");

        /*
         * 닉네임이 응답에 들어온 경우에만 업데이트합니다.
         * 닉네임이 없으면 기존 닉네임을 유지합니다.
         */
        String nickname =
            profile.path("nickname").asText("");

        if (!nickname.isBlank()) {
            if (!nicknameValidator.isValid(nickname)) {
                nickname = UUID.randomUUID().toString();
            }

            existingUser.setNickname(nickname);
        }

        /*
         * 카카오 이메일이 응답에 들어온 경우에만 업데이트합니다.
         * 이메일이 없으면 기존 이메일을 유지합니다.
         */
        String email =
            kakaoAccount.path("email").asText("");

        if (!email.isBlank()) {
            existingUser.setEmail(email);
        }

        /*
         * 카카오 프로필 사진이 응답에 들어온 경우에만 업데이트합니다.
         * 프로필 사진이 없으면 기존 사진을 유지합니다.
         */
        String profilePictureUrl =
            profile.path("profile_image_url").asText("");

        if (!profilePictureUrl.isBlank()) {
            existingUser.setProfilePic(profilePictureUrl);
        }

        userRepository.save(existingUser);

        /*
         * 카카오에서 최근에 받은 전체 사용자 정보를
         * 소셜 로그인 연결 정보에 갱신합니다.
         */
        kakaoSocialLoginUser.setExtraData(
            kakaoUserInfo.toString()
        );

        socialLoginUserRepository.save(
            kakaoSocialLoginUser
        );

        return existingUser;
    }

    /**
     * 새로운 일반 사용자를 생성합니다.
     *
     * @param nickname 닉네임
     * @param email 이메일
     * @param password 비밀번호
     * @return 생성된 사용자
     */
    @Transactional
    public User createUser(
        String nickname,
        String email,
        String password
    ) {
        boolean isValidNickname =
            nicknameValidator.isValid(nickname);

        if (!isValidNickname) {
            throw new InvalidRequestDataException(
                ErrorCode.INVALID_NICKNAME
            );
        }

        boolean existingByEmail =
            userRepository.existsByEmail(email);

        if (existingByEmail) {
            throw new InvalidRequestDataException(
                ErrorCode.INVALID_EMAIL
            );
        }

        boolean isValidPassword =
            passwordValidator.isValid(password);

        if (!isValidPassword) {
            throw new InvalidRequestDataException(
                ErrorCode.INVALID_PASSWORD
            );
        }

        Role userRole =
            roleService.getUserRole();

        String hashedPassword =
            passwordEncoder.encode(password);

        User newUser = User.builder()
            .nickname(nickname)
            .email(email)
            .password(hashedPassword)
            .role(userRole)
            .build();

        userRepository.save(newUser);

        return newUser;
    }

    /**
     * 사용자 삭제
     *
     * @param id 사용자 ID
     */
    @Transactional
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * 이메일 인증 토큰 만료
     *
     * @param user 사용자
     */
    @Transactional
    public void expireEmailVerificationToken(
        User user
    ) {
        Instant now = Instant.now();

        user.setEmailVerificationExpiry(now);

        userRepository.save(user);
    }

    /**
     * 사용자 ID로 사용자 프로필을 조회합니다.
     *
     * @param id 사용자 ID
     * @return 사용자 프로필
     */
    public UserProfile getUserProfileById(Long id) {
        User user =
            userRepository.findById(id)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND
                    )
                );

        return new UserProfile(
            user.getId(),
            user.getNickname(),
            user.getEmail(),
            user.getProfilePic() != null
                ? imgUtils.getImgUrl(user.getProfilePic())
                : null
        );
    }

    /**
     * 사용자 ID로 사용자 조회
     *
     * @param id 사용자 ID
     * @return 사용자 Optional
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * 이메일로 사용자 조회
     *
     * @param email 이메일
     * @return 사용자 Optional
     */
    public Optional<User> getUserByEmail(
        String email
    ) {
        return userRepository.findByEmail(email);
    }

    /**
     * 공개 UUID로 사용자 조회
     *
     * @param publicId 공개 UUID
     * @return 사용자
     */
    public User getUserByPublicId(UUID publicId) {
        return userRepository
            .findByPublicId(publicId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND
                )
            );
    }

    /**
     * 이메일 인증 실패 횟수 증가
     *
     * @param user 사용자
     */
    @Transactional
    public void incrementEmailVerificationFailCount(
        User user
    ) {
        userRepository.incrementEmailVerificationFailCount(
            user.getId()
        );
    }

    /**
     * 이메일 인증 실패 횟수 초기화
     *
     * @param user 사용자
     */
    @Transactional
    public void resetEmailVerificationFailCount(
        User user
    ) {
        userRepository.resetEmailVerificationFailCount(
            user.getId()
        );
    }

    /**
     * 사용자 비밀번호 설정
     *
     * @param user 사용자
     * @param rawPassword 원문 비밀번호
     */
    public void setUserPassword(
        User user,
        String rawPassword
    ) {
        String hashedPassword =
            passwordEncoder.encode(rawPassword);

        user.setPassword(hashedPassword);
    }

    /**
     * 사용자 프로필 사진 설정
     *
     * @param user 사용자
     * @param profilePicture 프로필 사진
     */
    public void setUserProfilePicture(
        User user,
        MultipartFile profilePicture
    ) {
        if (
            user == null ||
            profilePicture == null ||
            profilePicture.isEmpty()
        ) {
            return;
        }

        try {
            fileUploadService.uploadProfilePicture(
                user,
                profilePicture
            );
        } catch (IOException e) {
            throw new InvalidRequestDataException(
                ErrorCode.INVALID_IMAGE
            );
        }
    }

    /**
     * 이메일로 사용자 존재 여부 확인
     *
     * @param email 이메일
     * @return 존재하면 true
     */
    public boolean userExistsByEmail(
        String email
    ) {
        return userRepository.existsByEmail(email);
    }

    /**
     * 사용자 프로필 업데이트
     *
     * @param userId 사용자 ID
     * @param updatedProfile 업데이트 요청
     * @param profilePicture 프로필 사진
     * @return 업데이트된 프로필
     */
    @Transactional
    public UserProfile updateUserProfile(
        Long userId,
        UpdateUserProfileRequest updatedProfile,
        MultipartFile profilePicture
    ) {
        User user =
            userRepository.findById(userId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND
                    )
                );

        String newNickname =
            updatedProfile.nickname();

        if (
            newNickname != null &&
            !newNickname.isBlank()
        ) {
            boolean isValidNickname =
                nicknameValidator.isValid(newNickname);

            if (!isValidNickname) {
                throw new InvalidRequestDataException(
                    ErrorCode.INVALID_NICKNAME
                );
            }

            user.setNickname(newNickname);
        }

        userRepository.save(user);

        if (
            profilePicture != null &&
            !profilePicture.isEmpty()
        ) {
            setUserProfilePicture(
                user,
                profilePicture
            );
        }

        return new UserProfile(
            user.getId(),
            user.getNickname(),
            user.getEmail(),
            user.getProfilePic() != null
                ? imgUtils.getImgUrl(user.getProfilePic())
                : null
        );
    }

    /**
     * 닉네임으로 사용자 존재 여부 확인
     *
     * @param nickname 닉네임
     * @return 존재하면 true
     */
    public boolean userExistsByNickname(
        String nickname
    ) {
        return userRepository.existsByNickname(
            nickname
        );
    }

    /**
     * 사용자 ID로 사용자 존재 여부 확인
     *
     * @param id 사용자 ID
     * @return 존재하면 true
     */
    public boolean userExistsById(Long id) {
        return userRepository.existsById(id);
    }
}