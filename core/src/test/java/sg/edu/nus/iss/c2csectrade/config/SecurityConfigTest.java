package sg.edu.nus.iss.c2csectrade.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sg.edu.nus.iss.c2csectrade.security.JwtAuthenticationFilter;
import sg.edu.nus.iss.c2csectrade.security.JwtTokenProvider;
import sg.edu.nus.iss.c2csectrade.security.UserDetailsServiceImpl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The load balancer probes /actuator/health/liveness without a token, so the
 * security rules must let it through while everything else stays protected.
 */
@WebMvcTest(controllers = SecurityConfigTest.ProbeController.class)
class SecurityConfigTest {

    // Stands in for the application class, which would also start MyBatis
    @SpringBootConfiguration
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class, ProbeController.class})
    static class SecurityOnly {
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/actuator/health", "/actuator/health/liveness", "/api/orders"})
        String ok() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtTokenProvider tokenProvider;

    @MockitoBean
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void livenessProbeNeedsNoToken() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
    }

    @Test
    void healthNeedsNoToken() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void otherEndpointsStillNeedAToken() throws Exception {
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }
}
