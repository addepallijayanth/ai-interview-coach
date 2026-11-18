package com.example.coach.ai.model;

/** Everything one analysis produced; stored as analysis.result_json. */
public record AnalysisResult(ResumeProfile candidate, JobRequirements job, MatchResult match) {}
