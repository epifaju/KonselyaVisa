package com.konselyavisa.eligibility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.konselyavisa.common.exception.BusinessException;
import io.github.jamsesso.jsonlogic.JsonLogic;
import io.github.jamsesso.jsonlogic.JsonLogicException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonLogicEligibilityEvaluator {

    private final ObjectMapper objectMapper;
    private final JsonLogic jsonLogic = new JsonLogic();

    public JsonLogicEligibilityEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean evaluate(Map<String, Object> rules, Map<String, Object> facts) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        try {
            Object result = jsonLogic.apply(objectMapper.writeValueAsString(rules), facts);
            if (result instanceof Boolean bool) {
                return bool;
            }
            throw BusinessException.badRequest("error.eligibility.invalid_result");
        } catch (JsonLogicException | JsonProcessingException ex) {
            throw BusinessException.badRequest("error.eligibility.invalid_rules");
        }
    }

    public void validate(Map<String, Object> rules) {
        evaluate(rules, Map.of());
    }
}
