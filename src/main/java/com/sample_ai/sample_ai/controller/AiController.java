package com.sample_ai.sample_ai.controller;


import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sample_ai.sample_ai.response.AiResponse;

import java.util.Map;
@RestController
@RequestMapping("/api/ai")
public class AiController {

    // L'interfaccia agnostica di Spring AI per dialogare con gli LLM
    private final ChatModel chatModel;

    // Dependency Injection classica tramite costruttore
    public AiController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping("/generate")
    public AiResponse generate(@RequestParam(value = "message") String message) {
        
        // 1. Inizializziamo il convertitore specificando la classe target
        BeanOutputConverter<AiResponse> converter = new BeanOutputConverter<>(AiResponse.class);

        // 2. Creiamo un template per il prompt, iniettando le regole di formattazione JSON obbligatorie
        String template = """
                Spiega il seguente argomento: {utenteMessage}.
                
                {format}
                """;

        PromptTemplate promptTemplate = new PromptTemplate(template);
        
        // 3. Uniamo il messaggio dell'utente con le istruzioni del formato JSON generate automaticamente
        Prompt prompt = promptTemplate.create(Map.of(
                "utenteMessage", message,
                "format", converter.getFormat() 
        ));

        // 4. Invochiamo il modello
        String rawResponse = chatModel.call(prompt).getResult().getOutput().getContent();

        // 5. Convertiamo la stringa JSON nell'oggetto Java e lo restituiamo (Spring Boot lo mostrerà come JSON)
        return converter.convert(rawResponse);
    }
}
