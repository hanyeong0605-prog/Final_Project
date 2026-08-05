package com.jobpilot.api.domain.jobposting.service;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class SkillNameNormalizer {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("java", "Java"),
            Map.entry("spring", "Spring Framework"),
            Map.entry("springboot", "Spring Boot"),
            Map.entry("스프링", "Spring Framework"),
            Map.entry("스프링부트", "Spring Boot"),
            Map.entry("javascript", "JavaScript"),
            Map.entry("js", "JavaScript"),
            Map.entry("typescript", "TypeScript"),
            Map.entry("ts", "TypeScript"),
            Map.entry("nodejs", "Node.js"),
            Map.entry("node", "Node.js"),
            Map.entry("react", "React"),
            Map.entry("reactjs", "React"),
            Map.entry("vue", "Vue.js"),
            Map.entry("vuejs", "Vue.js"),
            Map.entry("nextjs", "Next.js"),
            Map.entry("mysql", "MySQL"),
            Map.entry("postgresql", "PostgreSQL"),
            Map.entry("aws", "AWS"),
            Map.entry("docker", "Docker"),
            Map.entry("kubernetes", "Kubernetes"),
            Map.entry("k8s", "Kubernetes"),
            Map.entry("git", "Git"),
            Map.entry("python", "Python"),
            Map.entry("django", "Django"),
            Map.entry("fastapi", "FastAPI"),
            Map.entry("kotlin", "Kotlin"),
            Map.entry("csharp", "C#"),
            Map.entry("dotnet", ".NET")
    );

    String normalize(String name) {
        if (name == null) return "";
        String trimmed = name.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty() || trimmed.length() > 100) return "";
        String key = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9가-힣]", "");
        return ALIASES.getOrDefault(key, trimmed);
    }
}
