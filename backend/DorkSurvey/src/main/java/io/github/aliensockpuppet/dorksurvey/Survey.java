package io.github.aliensockpuppet.dorksurvey;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Survey {
    public record Option(String id, String text) {
    }

    public record Question(String id, String prompt, List<Option> options) {
    }

    private final List<Question> questions;

    private Survey(List<Question> questions) {
        this.questions = questions;
    }

    public static Survey load(FileConfiguration config) {
        List<Question> out = new ArrayList<>();
        List<?> raw = config.getList("questions");
        if (raw != null) {
            for (Object entry : raw) {
                if (!(entry instanceof java.util.Map<?, ?> map)) {
                    continue;
                }
                String id = string(map.get("id"));
                String prompt = string(map.get("prompt"));
                if (id.isEmpty() || prompt.isEmpty()) {
                    continue;
                }
                Object enabled = map.get("enabled");
                if (enabled != null && !Boolean.parseBoolean(enabled.toString())) {
                    continue;
                }
                List<Option> options = new ArrayList<>();
                Object opts = map.get("options");
                if (opts instanceof List<?> list) {
                    for (Object o : list) {
                        if (o instanceof java.util.Map<?, ?> om) {
                            String oid = string(om.get("id"));
                            String text = string(om.get("text"));
                            if (!oid.isEmpty() && !text.isEmpty()) {
                                options.add(new Option(oid, text));
                            }
                        }
                    }
                }
                if (!options.isEmpty()) {
                    out.add(new Question(id, prompt, options));
                }
            }
        }
        return new Survey(out);
    }

    public List<Question> questions() {
        return questions;
    }

    public boolean isEmpty() {
        return questions.isEmpty();
    }

    public Optional<Question> question(String id) {
        for (Question q : questions) {
            if (q.id().equals(id)) {
                return Optional.of(q);
            }
        }
        return Optional.empty();
    }

    public Optional<Question> firstUnanswered(java.util.Set<String> answeredIds) {
        for (Question q : questions) {
            if (!answeredIds.contains(q.id())) {
                return Optional.of(q);
            }
        }
        return Optional.empty();
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
