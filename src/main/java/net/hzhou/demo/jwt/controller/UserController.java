package net.hzhou.demo.jwt.controller;

import java.util.Optional;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import net.hzhou.demo.jwt.domain.JwtToken;
import net.hzhou.demo.jwt.domain.JwtUser;
import net.hzhou.demo.jwt.entity.RefreshToken;
import net.hzhou.demo.jwt.entity.SiteUser;
import net.hzhou.demo.jwt.exception.WebClientRuntimeException;
import net.hzhou.demo.jwt.repository.RefreshTokenRepository;
import net.hzhou.demo.jwt.repository.UserRepository;
import net.hzhou.demo.jwt.utils.JwtUtils;

@RestController
@Slf4j
public class UserController {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder bCryptPasswordEncoder;
  private final RefreshTokenRepository tokenRepository;

  public UserController(
      UserRepository userRepository,
      BCryptPasswordEncoder bCryptPasswordEncoder,
      RefreshTokenRepository tokenRepository) {
    this.userRepository = userRepository;
    this.bCryptPasswordEncoder = bCryptPasswordEncoder;
    this.tokenRepository = tokenRepository;
  }

  @PostMapping("/signup")
  public ResponseEntity<JwtUser> signUp(@Valid @RequestBody SiteUser siteUser) {
    siteUser.setPassword(bCryptPasswordEncoder.encode(siteUser.getPassword()));
    siteUser = userRepository.save(siteUser);
    if (siteUser.getId() > 0) {
      return ResponseEntity.ok(new JwtUser(siteUser.getId(), siteUser.getUsername()));
    } else {
      throw new RuntimeException("Failed to signup, please try again later.");
    }
  }

  @PostMapping("/refresh_token")
  public ResponseEntity<JwtToken> refreshToken(@Valid @RequestBody JwtToken jwtToken) {
    // 1. No expiry, and valid.
    JwtUser user = JwtUtils.parseRefreshToken(jwtToken.getRefreshToken());
    // 2. Not deleted from database.
    Optional<RefreshToken> token =
        tokenRepository.findByUserIdAndRefreshToken(user.getId(), jwtToken.getRefreshToken());
    if (token.isPresent()) {
      String newToken = JwtUtils.createAccessToken(user);
      return ResponseEntity.ok(new JwtToken(newToken, null));
    } else {
      throw new WebClientRuntimeException("Invalid refresh token provided!");
    }
  }

  @Transactional
  @DeleteMapping("/refresh_tokens")
  public ResponseEntity<Void> cleanRefreshTokens(Authentication auth) {
    JwtUser user = (JwtUser) auth.getPrincipal();
    log.info("[cleanRefreshTokens] user = {}", user);
    tokenRepository.deleteAllByUserId(user.getId());
    return ResponseEntity.ok().build();
  }
}
