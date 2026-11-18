package com.example.coach.ai.agent;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The kinds of question a real interview loop mixes. The descriptions are shown to the interviewer model. */
public enum QuestionCategory {

    TECHNICAL("Technical", "Core knowledge the job needs: concepts, languages, frameworks and tools from the job "
            + "description, debugging, best practices and trade-offs."),
    PROBLEM_SOLVING("Problem solving", "A concrete problem to reason through out loud. For engineering roles, an "
            + "algorithm or data-structure problem (explain the approach and its time and space complexity; no code "
            + "needed). For other roles, a case or estimation question."),
    SYSTEM_DESIGN("System design", "Design something end to end: architecture, APIs, data model, scalability, "
            + "reliability and the trade-offs between options. For non-engineering roles, design a process, plan or "
            + "strategy instead."),
    PROJECT("Project deep-dive", "A specific project or achievement from the candidate's resume: their exact role, "
            + "key decisions, challenges, results and what they would do differently."),
    BEHAVIOURAL("Behavioural", "A real past situation, best answered in STAR form (situation, task, action, "
            + "result): teamwork, conflict, failure, ownership, leadership, feedback or pressure."),
    SITUATIONAL("Situational", "A realistic hypothetical (\"What would you do if...\"), such as a production "
            + "incident, a slipping deadline, unclear requirements or a difficult stakeholder."),
    HR("HR", "Motivation and fit: introducing themselves, why this role and company, career goals, strengths and "
            + "weaknesses, work style and expectations.");

    private final String label;
    private final String description;

    QuestionCategory(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** The category guide for the interviewer's system prompt, one indented line per category. */
    public static String promptGuide() {
        return Arrays.stream(values())
                .map(category -> "   - " + category.label + ": " + category.description)
                .collect(Collectors.joining("\n"));
    }

    /** Parses a stored category name; {@code null} for interviews created before categories existed. */
    public static QuestionCategory fromName(String name) {
        return name == null ? null : valueOf(name);
    }
}
