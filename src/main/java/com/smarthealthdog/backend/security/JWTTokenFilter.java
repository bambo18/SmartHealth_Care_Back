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

@Component
public class JWTTokenFilter extends OncePerRequestFilter {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private CustomUserDetailsService userDetailsService;
    // private static final Logger log = LoggerFactory.getLogger(JWTTokenFilter.class);

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null) {
                refreshTokenService.validateAccessToken(jwt); // Validate the token and throw exception if invalid
                String userPublicId = refreshTokenService.getClaimsFromToken(jwt).getPayload().getSubject();
                UserDetails userDetails = userDetailsService.loadUserByUsername(userPublicId);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                    );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            if (BadCredentialsException.class.equals(e.getClass()) || e.getCause() instanceof BadCredentialsException) {
                BadCredentialsException ex = (BadCredentialsException) (e.getCause() != null ? e.getCause() : e);

                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                // write to response
                response.getWriter().write(objectMapper.writeValueAsString(
                    new ErrorMessage(
                        List.of(ex.getErrorCode().name()),
                        List.of(ex.getErrorCode().getMessage())
                    )
                ));
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("application/json");
                response.getWriter().write(objectMapper.writeValueAsString(
                    new ErrorMessage(
                        List.of(ErrorCode.INTERNAL_SERVER_ERROR.name()),
                        List.of(ErrorCode.INTERNAL_SERVER_ERROR.getMessage())
                    )
                )); 
            }

            return;
        }
        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (headerAuth != null && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
