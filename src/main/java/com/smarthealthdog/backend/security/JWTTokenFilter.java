package com.smarthealthdog.backend.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthealthdog.backend.dto.ErrorMessage;
import com.smarthealthdog.backend.exceptions.BadCredentialsException;
import com.smarthealthdog.backend.services.CustomUserDetailsService;
import com.smarthealthdog.backend.services.RefreshTokenService;
import com.smarthealthdog.backend.validation.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JWTTokenFilter extends OncePerRequestFilter {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        try {

            String jwt = parseJwt(request);

            if (jwt != null) {

                /*
                 * Access Token 유효성 검사
                 */
                refreshTokenService.validateAccessToken(jwt);

                /*
                 * JWT subject에 저장된 사용자 publicId(UUID)를 가져옵니다.
                 */
                String userPublicId =
                    refreshTokenService
                        .getClaimsFromToken(jwt)
                        .getPayload()
                        .getSubject();

                /*
                 * 사용자 정보 및 권한 조회
                 */
                UserDetails userDetails =
                    userDetailsService
                        .loadUserByUsername(
                            userPublicId
                        );

                /*
                 * 인증 객체 생성
                 */
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );

                authentication.setDetails(
                    new WebAuthenticationDetailsSource()
                        .buildDetails(request)
                );

                /*
                 * Spring Security Context에 인증 정보 저장
                 */
                SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
            }

        } catch (Exception e) {

            /*
             * 인증 실패 계열 예외
             */
            if (
                e instanceof BadCredentialsException ||
                e.getCause() instanceof BadCredentialsException
            ) {

                BadCredentialsException ex;

                if (e instanceof BadCredentialsException) {
                    ex = (BadCredentialsException) e;
                } else {
                    ex =
                        (BadCredentialsException) e.getCause();
                }

                response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED
                );

                response.setCharacterEncoding("UTF-8");
                response.setContentType(
                    "application/json;charset=UTF-8"
                );

                response.getWriter().write(
                    objectMapper.writeValueAsString(
                        new ErrorMessage(
                            List.of(
                                ex.getErrorCode().name()
                            ),
                            List.of(
                                ex.getErrorCode()
                                    .getMessage()
                            )
                        )
                    )
                );

            } else {

                /*
                 * 예상하지 못한 인증 처리 오류는
                 * 서버 로그에 Stack Trace를 남깁니다.
                 *
                 * 앞으로 보호 API에서 500이 발생하면
                 * docker logs를 통해 실제 원인을 확인할 수 있습니다.
                 */
                log.error(
                    "JWT authentication failed. method={}, uri={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    e
                );

                response.setStatus(
                    HttpServletResponse
                        .SC_INTERNAL_SERVER_ERROR
                );

                response.setCharacterEncoding("UTF-8");
                response.setContentType(
                    "application/json;charset=UTF-8"
                );

                response.getWriter().write(
                    objectMapper.writeValueAsString(
                        new ErrorMessage(
                            List.of(
                                ErrorCode
                                    .INTERNAL_SERVER_ERROR
                                    .name()
                            ),
                            List.of(
                                ErrorCode
                                    .INTERNAL_SERVER_ERROR
                                    .getMessage()
                            )
                        )
                    )
                );
            }

            return;
        }

        /*
         * JWT가 없거나 정상 인증되었다면
         * 다음 Filter로 요청을 전달합니다.
         */
        filterChain.doFilter(
            request,
            response
        );
    }

    /**
     * Authorization Header에서 JWT를 추출합니다.
     *
     * 형식:
     * Authorization: Bearer {token}
     */
    private String parseJwt(
        HttpServletRequest request
    ) {

        String headerAuth =
            request.getHeader("Authorization");

        if (
            headerAuth != null &&
            headerAuth.startsWith("Bearer ")
        ) {
            return headerAuth.substring(7);
        }

        return null;
    }
}