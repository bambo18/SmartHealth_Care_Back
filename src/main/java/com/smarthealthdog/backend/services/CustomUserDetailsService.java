package com.smarthealthdog.backend.services;

import com.smarthealthdog.backend.domain.RoleEnum;
import com.smarthealthdog.backend.domain.User;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.repositories.UserRepository;
import com.smarthealthdog.backend.validation.ErrorCode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    /**
     * UserDetails 객체를 UUID 또는 이메일을 사용하여 로드합니다.
     *
     * @param username UUID 또는 이메일 문자열
     * @return UserDetails
     * @throws UsernameNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Override
    public UserDetails loadUserByUsername(String username)
        throws UsernameNotFoundException {

        Optional<User> userOptional;
        List<GrantedAuthority> authorities = new ArrayList<>();

        boolean isUUID;

        try {
            UUID.fromString(username);
            isUUID = true;
        } catch (IllegalArgumentException e) {
            isUUID = false;
        }

        /*
         * UUID로 파싱이 가능하면
         * 이미 JWT 로그인이 완료된 사용자의 요청으로 판단하고
         * Role과 Permission 정보까지 함께 조회합니다.
         */
        if (isUUID) {
            UUID userId = UUID.fromString(username);

            userOptional =
                userRepository
                    .findUserWithRoleAndPermissionsByPublicId(
                        userId
                    );
        } else {
            /*
             * 일반 이메일/비밀번호 로그인 시
             * 이메일로 사용자를 조회합니다.
             */
            userOptional =
                userRepository.findByEmail(username);
        }

        if (userOptional.isEmpty()) {
            throw new BadCredentialsException(
                ErrorCode.LOGIN_FAILURE
            );
        }

        User user = userOptional.get();

        /*
         * 이미 로그인된 사용자(JWT 인증)인 경우
         * Role 및 Permission 정보를 UserDetails에 추가합니다.
         */
        if (isUUID) {

            if (user.getRole() == null) {
                throw new BadCredentialsException(
                    ErrorCode.LOGIN_FAILURE
                );
            }

            /*
             * 정지 또는 삭제된 사용자는 인증 불가
             */
            if (
                user.getRole().getName() == RoleEnum.BANNED_USER ||
                user.getRole().getName() == RoleEnum.DELETED_USER
            ) {
                throw new BadCredentialsException(
                    ErrorCode.LOGIN_FAILURE
                );
            }

            /*
             * 사용자의 Permission을
             * Spring Security Authority로 변환합니다.
             */
            if (
                user.getRole().getPermissions() != null &&
                !user.getRole().getPermissions().isEmpty()
            ) {
                user.getRole()
                    .getPermissions()
                    .forEach(permission -> {
                        authorities.add(
                            new SimpleGrantedAuthority(
                                permission
                                    .getName()
                                    .getName()
                            )
                        );
                    });
            }

        } else {

            /*
             * 이메일/비밀번호 방식으로 로그인하면 안 되는 Role
             *
             * SOCIAL_ACCOUNT_USER는 카카오 등
             * 소셜 로그인 전용 사용자이므로
             * 일반 비밀번호 로그인을 차단합니다.
             */
            List<RoleEnum> loginProhibitedRoles = List.of(
                RoleEnum.BANNED_USER,
                RoleEnum.DELETED_USER,
                RoleEnum.SOCIAL_ACCOUNT_USER
            );

            if (
                user.getRole() == null ||
                loginProhibitedRoles.contains(
                    user.getRole().getName()
                )
            ) {
                throw new BadCredentialsException(
                    ErrorCode.LOGIN_FAILURE
                );
            }
        }

        /*
         * 카카오 등 소셜 로그인 계정은
         * DB password 값이 null일 수 있습니다.
         *
         * Spring Security UserDetails 객체는
         * null password를 허용하지 않으므로
         * UserDetails 생성 시에만 placeholder 값을 사용합니다.
         *
         * 이 값은 실제 로그인 비밀번호로 사용되지 않습니다.
         */
        String password = user.getPassword();

        if (password == null) {
            password = "SOCIAL_LOGIN_ONLY";
        }

        return new org.springframework.security.core.userdetails.User(
            user.getId().toString(),
            password,
            authorities
        );
    }
}