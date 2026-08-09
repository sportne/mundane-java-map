package io.github.mundanej.map.example.vaadin;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Test-lane-only endpoint that exercises the real servlet-session destruction path. */
@RestController
final class BrowserSessionCloseController {
    @PostMapping("/browser-evidence/close-session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void closeSession(HttpSession session) {
        session.invalidate();
    }
}
