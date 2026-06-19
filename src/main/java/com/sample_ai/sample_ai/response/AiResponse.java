package com.sample_ai.sample_ai.response;

import java.util.List;

public record AiResponse(
    String concept,
    String structuredExplanation,
    List<String> keyPoints,
    String technicalComplexity // es: Facile, Medio, Avanzato
) {}