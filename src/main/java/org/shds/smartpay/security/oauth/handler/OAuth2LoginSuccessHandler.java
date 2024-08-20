package org.shds.smartpay.security.oauth.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.shds.smartpay.entity.MemberRole;
import org.shds.smartpay.repository.MemberRepository;
import org.shds.smartpay.security.oauth.CustomOAuth2User;
import org.shds.smartpay.security.service.JwtService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {
    private final JwtService jwtService;
    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        log.info("OAuth2 Login 성공!");
        try {
            CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
            log.info("OAuth2 Login 성공핸들러 들어와서 ROLE을 잘 가져오는가? : {}",oAuth2User.getRole());
            // User의 Role이 GUEST일 경우 처음 요청한 회원이므로 회원가입 페이지로 리다이렉트
            if(oAuth2User.getRole() == MemberRole.GUEST) {
                String accessToken = jwtService.createAccessToken(oAuth2User.getEmail());
                response.addHeader(jwtService.getAccessHeader(), "Bearer " + accessToken);

                // 사용자 정보를 URL 파라미터로 추가하여 리다이렉트
                //String redirectUrl = "/oauth2/sign-up?email=" + oAuth2User.getEmail() + "&role=" + oAuth2User.getRole();
                //response.sendRedirect(redirectUrl); // 프론트의 회원가입 추가 정보 입력 폼으로 리다이렉트
                // ObjectMapper를 사용하여 JSON 문자열 생성
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> responseData = new HashMap<>();
                responseData.put("email", oAuth2User.getEmail());
                responseData.put("role", oAuth2User.getRole().toString());

                String jsonResponse = objectMapper.writeValueAsString(responseData);

                // 응답으로 JSON 데이터 보내기
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_OK);

                // 응답 바디에 JSON 쓰기
                response.getWriter().write(jsonResponse);

                jwtService.sendAccessAndRefreshToken(response, accessToken, null);
//                User findUser = userRepository.findByEmail(oAuth2User.getEmail())
//                                .orElseThrow(() -> new IllegalArgumentException("이메일에 해당하는 유저가 없습니다."));
//                findUser.authorizeUser();
            } else {
                loginSuccess(response, oAuth2User); // 로그인에 성공한 경우 access, refresh 토큰 생성
            }
        } catch (Exception e) {
            throw e;
        }
    }

    // TODO : 소셜 로그인 시에도 무조건 토큰 생성하지 말고 JWT 인증 필터처럼 RefreshToken 유/무에 따라 다르게 처리해보기
    private void loginSuccess(HttpServletResponse response, CustomOAuth2User oAuth2User) throws IOException {
        log.info("실제 MEMBER로 회원가입성공");
        String accessToken = jwtService.createAccessToken(oAuth2User.getEmail());
        String refreshToken = jwtService.createRefreshToken();
        response.addHeader(jwtService.getAccessHeader(), "Bearer " + accessToken);
        response.addHeader(jwtService.getRefreshHeader(), "Bearer " + refreshToken);
        log.info("loginSuccess 메서드 들어와짐?");

        jwtService.sendAccessAndRefreshToken(response, accessToken, refreshToken);
        jwtService.updateRefreshToken(oAuth2User.getEmail(), refreshToken);

        // 리다이렉트
        response.sendRedirect("/login");
    }
}