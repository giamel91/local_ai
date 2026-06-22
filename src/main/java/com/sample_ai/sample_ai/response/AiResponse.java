package com.sample_ai.sample_ai.response;

import java.util.List;


public record AiResponse(
    String argomento, 
    String spiegazioneTecnica, 
    String riassuntoSemplice
) {}