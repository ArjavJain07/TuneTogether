package com.tunetogether.auth;

import com.tunetogether.auth.dto.AuthResponse;
import com.tunetogether.auth.dto.GoogleLoginRequest;
import com.tunetogether.auth.dto.RefreshRequest;
import com.tunetogether.auth.dto.RefreshResponse;
import com.tunetogether.auth.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleIdTokenService googleIdTokenService;
    private final AppUserRepository appUserRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            GoogleIdTokenService googleIdTokenService,
            AppUserRepository appUserRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.googleIdTokenService = googleIdTokenService;
        this.appUserRepository = appUserRepository;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/google")
    @Transactional
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleIdTokenService.GoogleUserInfo googleUser = googleIdTokenService.verify(request.idToken());

        AppUser appUser = appUserRepository.findByGoogleSub(googleUser.sub())
                .map(existing -> {
                    existing.touchLogin(googleUser.name(), googleUser.picture());
                    return existing;
                })
                .orElseGet(() -> appUserRepository.save(
                        new AppUser(googleUser.sub(), googleUser.email(), googleUser.name(), googleUser.picture())));
        appUserRepository.save(appUser);

        TuneTogetherPrincipal principal = new TuneTogetherPrincipal(
                "u" + appUser.getId(), appUser.getId(), appUser.getDisplayName(), false, List.of("USER"));

        String accessToken = jwtService.issueAccessToken(principal);
        String refreshToken = refreshTokenService.issueFor(appUser);

        UserDto userDto = new UserDto(appUser.getDisplayName(), appUser.getEmail(), appUser.getPictureUrl(),
                principal.subjectId(), false);
        return new AuthResponse(accessToken, refreshToken, userDto);
    }

    @PostMapping("/guest")
    public AuthResponse loginAsGuest() {
        String name = "Guest_" + (1000 + RANDOM.nextInt(9000));
        String subjectId = "g" + UUID.randomUUID();

        TuneTogetherPrincipal principal = new TuneTogetherPrincipal(subjectId, null, name, true, List.of("GUEST"));
        String accessToken = jwtService.issueAccessToken(principal);

        UserDto userDto = new UserDto(name, null, null, subjectId, true);
        return new AuthResponse(accessToken, null, userDto);
    }

    @PostMapping("/refresh")
    @Transactional
    public RefreshResponse refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.rotate(request.refreshToken());
        AppUser user = rotation.user();

        TuneTogetherPrincipal principal = new TuneTogetherPrincipal(
                "u" + user.getId(), user.getId(), user.getDisplayName(), false, List.of("USER"));
        String accessToken = jwtService.issueAccessToken(principal);

        return new RefreshResponse(accessToken, rotation.rawRefreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenService.revoke(request.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(Authentication authentication) {
        TuneTogetherPrincipal principal = (TuneTogetherPrincipal) authentication.getPrincipal();
        return new UserDto(principal.displayName(), null, null, principal.subjectId(), principal.guest());
    }
}
