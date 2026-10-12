package sg.edu.nus.iss.c2csectrade.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import sg.edu.nus.iss.c2csectrade.config.SecurityConfig;
import sg.edu.nus.iss.c2csectrade.security.JwtAuthenticationFilter;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.security.UserDetailsServiceImpl;
import sg.edu.nus.iss.c2csectrade.service.SearchClient;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Only an administrator may rebuild the index; Search itself cannot check roles. */
@WebMvcTest(controllers = SearchIndexController.class)
class SearchIndexControllerTest {

    // Stands in for the application class, which would also start MyBatis
    @SpringBootConfiguration
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, SearchIndexController.class})
    static class SecurityOnly {
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @MockitoBean
    private SearchClient searchClient;

    @Test
    void administratorRebuildsTheIndex() throws Exception {
        signedInAs("ROLE_ADMIN");
        when(searchClient.reindex()).thenReturn(new SearchClient.ReindexResult(12, 2));

        mvc.perform(post("/api/admin/search/reindex").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(12))
                .andExpect(jsonPath("$.removed").value(2));
    }

    @Test
    void anonymousIsRefused() throws Exception {
        mvc.perform(post("/api/admin/search/reindex")).andExpect(status().isUnauthorized());
        verify(searchClient, never()).reindex();
    }

    @Test
    void ordinaryUserIsRefused() throws Exception {
        signedInAs("ROLE_USER");

        mvc.perform(post("/api/admin/search/reindex").header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verify(searchClient, never()).reindex();
    }

    @Test
    void rebuildAlreadyRunning() throws Exception {
        signedInAs("ROLE_ADMIN");
        when(searchClient.reindex()).thenThrow(new SearchClient.ReindexInProgressException());

        mvc.perform(post("/api/admin/search/reindex").header("Authorization", "Bearer token"))
                .andExpect(status().isConflict());
    }

    @Test
    void searchUnavailable() throws Exception {
        signedInAs("ROLE_ADMIN");
        when(searchClient.reindex()).thenThrow(
                new SearchClient.SearchUnavailableException(new RuntimeException("connection refused")));

        mvc.perform(post("/api/admin/search/reindex").header("Authorization", "Bearer token"))
                .andExpect(status().isServiceUnavailable());
    }

    private void signedInAs(String role) {
        when(tokenProvider.validateToken("token")).thenReturn(true);
        when(tokenProvider.getUsernameFromToken("token")).thenReturn("someone");
        when(userDetailsService.loadUserByUsername("someone"))
                .thenReturn(new User("someone", "", List.of(new SimpleGrantedAuthority(role))));
    }
}
