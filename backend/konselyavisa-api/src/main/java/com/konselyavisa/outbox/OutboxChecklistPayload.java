package com.konselyavisa.outbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sanitized document checklist for outbox → n8n emails. No eligibility or pricing decisions.
 */
public final class OutboxChecklistPayload {

    private OutboxChecklistPayload() {}

    public static List<Map<String, Object>> fromRequirements(List<Map<String, Object>> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> checklist = new ArrayList<>(requirements.size());
        for (Map<String, Object> requirement : requirements) {
            if (requirement == null) {
                continue;
            }
            Object code = requirement.get("code");
            if (code == null || code.toString().isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", code.toString());
            item.put("required", requirement.get("required") instanceof Boolean b ? b : Boolean.TRUE);
            Object labels = requirement.get("labelI18n");
            if (labels instanceof Map<?, ?> map) {
                Map<String, Object> labelI18n = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        labelI18n.put(entry.getKey().toString(), entry.getValue().toString());
                    }
                }
                if (!labelI18n.isEmpty()) {
                    item.put("labelI18n", labelI18n);
                }
            }
            checklist.add(item);
        }
        return checklist;
    }
}
