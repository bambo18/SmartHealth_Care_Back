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
     * @param user
     * @param rawPassword
     * @return true 만약 비밀번호가 일치하는 경우, false otherwise
     */
    public boolean checkUserPassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /**
     * 카카오 소셜 로그인으로 사용자 생성
     * @param kakaoUserInfo 카카오에서 제공하는 사용자 정보 JSON
     * @return 생성된 사용자 객체
     */
    @Transactional
    public User createUserWithKakaoUserInfo(JsonNode kakaoUserInfo) {
        if (kakaoUserInfo == null || kakaoUserInfo.isEmpty()) {
            throw new IllegalArgumentException("카카오 사용자 정보가 유효하지 않습니다.");
        }

        JsonNode kakaoAccount = kakaoUserInfo.get("kakao_account");
        if (kakaoAccount == null || kakaoAccount.isEmpty()) {
            throw new InternalServerErrorException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        String email = kakaoAccount.path("email").asText();
        if (email == null || email.isBlank()) {
            email = "kakao_" + UUID.randomUUID().toString().replaceAll("-", "") + "@noemail.smarthealthdog.com";
        }

        JsonNode profile = kakaoAccount.path("profile");

        String nickname = profile.path("nickname").asText();
        if (nickname == null || nickname.isBlank() || !nicknameValidator.isValid(nickname)) {
            String uuid = UUID.randomUUID().toString();
            nickname = uuid;
        }

        String profilePictureUrl = profile.path("profile_image_url").asText();
        if (profilePictureUrl == null || profilePictureUrl.isBlank()) {
            profilePictureUrl = null;
        }
        
        // 3. User Object Assembly
        Role userRole = roleService.getSocialUserRole();
        
        // Kakao 소셜 로그인의 경우 비밀번호는 null로 설정
        User newUser = User.builder()
                            .nickname(nickname) // The robustly determined nickname
                            .email(email)       // The validated email
                            .password(null)     // Social login has no password
                            .profilePic(profilePictureUrl)
                            .role(userRole)
                            .build();
        userRepository.save(newUser);

        SocialLoginProvider kakaoProvider = socialLoginProviderService.getKakaoProvider();
        SocialLoginUser socialLoginUser = SocialLoginUser.builder()
            .user(newUser)
            .provider(kakaoProvider)
            .providerUserId(kakaoUserInfo.path("id").asText())
            .extraData(kakaoUserInfo.toString())
            .build();
        socialLoginUserRepository.save(socialLoginUser);

        return newUser;
    }

    /**
     * 카카오 소셜 로그인으로 기존 사용자 정보 업데이트
     * @param existingUser 기존 사용자 객체
     * @param kakaoSocialLoginUser 카카오 소셜 로그인 연결 정보 객체
     * @param kakaoUserInfo 카카오에서 제공하는 사용자 정보 JSON
     * @return 업데이트된 사용자 객체
     */
    @Transactional
    public User updateUserWithKakaoUserInfo(
            User existingUser, 
            SocialLoginUser kakaoSocialLoginUser, 
            JsonNode kakaoUserInfo
    ) {
        String nickname = kakaoUserInfo.path("kakao_account").path("profile").get("nickname").asText();
        if (!nicknameValidator.isValid(nickname)) {
            String uuid = UUID.randomUUID().toString();
            nickname = uuid;
        }
        existingUser.setNickname(nickname);

        String email = kakaoUserInfo.path("kakao_account").get("email").asText();
        if (email != null && !email.isBlank()) {
            existingUser.setEmail(email);
        }

        String profilePictureUrl = kakaoUserInfo.path("kakao_account").path("profile").get("profile_image_url").asText();
        existingUser.setProfilePic(profilePictureUrl);

        userRepository.save(existingUser);

        kakaoSocialLoginUser.setExtraData(kakaoUserInfo.toString());
        socialLoginUserRepository.save(kakaoSocialLoginUser);

        return existingUser;
    }

    /**
     * 새로운 사용자 생성
     * @param nickname 3-128 characters
     * @param email 유효한 이메일 형식
     * @param password 8-256자, 대문자, 소문자, 숫자, 특수문자 포함
     * @return 생성된 사용자 객체
     */
    @Transactional
    public User createUser(String nickname, String email, String password) {
        boolean isValidNickname = nicknameValidator.isValid(nickname);
        if (!isValidNickname) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_NICKNAME);
        }

        boolean existingByEmail = userRepository.existsByEmail(email);
        if (existingByEmail) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_EMAIL);
        }

        boolean isValidPassword = passwordValidator.isValid(password);
        if (!isValidPassword) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_PASSWORD);
        }

        Role userRole = roleService.getUserRole();

        String hashedPassword = passwordEncoder.encode(password);
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
     * @param id
     */
    @Transactional
    public void deleteUser(Long id) {
        // Logic to delete a user
        userRepository.deleteById(id);
    }

    /**
     * 이메일 인증 토큰 만료
     * @param user
     */
    @Transactional
    public void expireEmailVerificationToken(User user) {
        Instant now = Instant.now();
        user.setEmailVerificationExpiry(now);
        userRepository.save(user);
    }

    /**
     * ID로 사용자 프로필 조회
     * @param id
     * @return 사용자 프로필
     * @throws ResourceNotFoundException 사용자가 존재하지 않을 경우 발생
     */
    public UserProfile getUserProfileById(Long id) {
        // Logic to retrieve a user profile by ID
        User user = userRepository.findById(id).orElseThrow(
            () -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        return new UserProfile(
            user.getId(),
            user.getNickname(),
            user.getEmail(),
            user.getProfilePic() != null ? imgUtils.getImgUrl(user.getProfilePic()) : null
        );
    }

    /**
     * ID로 사용자 조회
     * @param id
     * @return 사용자 객체
     */
    public Optional<User> getUserById(Long id) {
        // Logic to retrieve a user by ID
        return userRepository.findById(id);
    }

    /**
     * 이메일로 사용자 조회
     * @param email
     * @return 사용자 객체
     */
    public Optional<User> getUserByEmail(String email) {
        // Logic to retrieve a user by email
        return userRepository.findByEmail(email);
    }

    public User getUserByPublicId(UUID publicId) {
        return userRepository.findByPublicId(publicId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
    }


    /**
     * 이메일 인증 실패 횟수 증가
     * @param user
     */
    @Transactional
    public void incrementEmailVerificationFailCount(User user) {
        userRepository.incrementEmailVerificationFailCount(user.getId());
    }

    /**
     * 이메일 인증 실패 횟수 초기화
     * @param user
     */
    @Transactional
    public void resetEmailVerificationFailCount(User user) {
        userRepository.resetEmailVerificationFailCount(user.getId());
    }

    /**
     * 사용자 비밀번호 설정 (해싱 포함)
     * @param user
     * @param rawPassword
     */
    public void setUserPassword(User user, String rawPassword) {
        String hashedPassword = passwordEncoder.encode(rawPassword);
        user.setPassword(hashedPassword);
    }

    /**
     * 사용자 프로필 사진 설정
     * @param user 사용자 객체
     * @param profilePicture 업로드할 프로필 사진 파일
     */
    public void setUserProfilePicture(User user, MultipartFile profilePicture) {
        if (user == null || profilePicture == null || profilePicture.isEmpty()) {
            return;
        }

        // 이미지 유효성 검사
        try {
            fileUploadService.uploadProfilePicture(user, profilePicture);
        } catch (IOException e) {
            throw new InvalidRequestDataException(ErrorCode.INVALID_IMAGE);
        }
    }

    /**
     * 이메일로 사용자 존재 여부 확인
     * @param email
     * @return true if user exists, false otherwise
     */
    public boolean userExistsByEmail(String email) {
        // Logic to check if a user exists by email
        return userRepository.existsByEmail(email);
    }

    /**
     * 사용자 프로필 업데이트
     * @param userId 사용자 ID
     * @param updatedProfile 업데이트할 프로필 정보
     * @param profilePicture 업로드할 프로필 사진 파일
     * @return 업데이트된 사용자 프로필
     */
    @Transactional
    public UserProfile updateUserProfile(Long userId, UpdateUserProfileRequest updatedProfile, MultipartFile profilePicture) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));

        // 닉네임 업데이트
        String newNickname = updatedProfile.nickname();
        if (newNickname != null && !newNickname.isBlank()) {
            boolean isValidNickname = nicknameValidator.isValid(newNickname);
            if (!isValidNickname) {
                throw new InvalidRequestDataException(ErrorCode.INVALID_NICKNAME);
            }
            user.setNickname(newNickname);
        }

        userRepository.save(user);

        // 프로필 사진 업데이트
        if (profilePicture != null && !profilePicture.isEmpty()) {
            setUserProfilePicture(user, profilePicture);
        }

        return new UserProfile(
            user.getId(),
            user.getNickname(),
            user.getEmail(),
            user.getProfilePic() != null ? imgUtils.getImgUrl(user.getProfilePic()) : null
        );
    }

    /**
     * 닉네임으로 사용자 존재 여부 확인
     * @param nickname
     * @return Boolean
     */
    public boolean userExistsByNickname(String nickname) {
        // Logic to check if a user exists by nickname
        return userRepository.existsByNickname(nickname);
    }

    /**
     * ID로 사용자 존재 여부 확인
     * @param id
     * @return Boolean
     */
    public boolean userExistsById(Long id) {
        // Logic to check if a user exists by ID
        return userRepository.existsById(id);
    }
}
