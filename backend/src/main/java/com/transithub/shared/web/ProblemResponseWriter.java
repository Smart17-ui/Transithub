package com.transithub.shared.web;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an RFC 9457 problem response directly, for code that runs before
 * Spring MVC's exception handling, such as servlet filters and security
 * handlers. The format matches {@link GlobalExceptionHandler}'s.
 */
@Component
public class ProblemResponseWriter {

    private final JsonMapper json;

    public ProblemResponseWriter(JsonMapper json) {
        this.json = json;
    }

    public void write(HttpServletResponse response, HttpStatus status,
                      String title, String detail, String code) throws IOException {

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "about:blank");
        problem.put("title", title);
        problem.put("status", status.value());
        problem.put("detail", detail);
        problem.put(ProblemCodes.PROPERTY, code);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(json.writeValueAsBytes(problem));
    }
}
