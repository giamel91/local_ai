package com.sample_ai.sample_ai.controller;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sample_ai.sample_ai.response.AiResponse;
import com.sample_ai.sample_ai.service.KafkaConsumerService;
import com.sample_ai.sample_ai.service.KafkaProducerService;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:4200") // SBLOCCA LE CHIAMATE DA ANGULAR
public class AiController {

    private final ChatClient chatClient;
    private final KafkaProducerService kafkaProducer;
    private final KafkaConsumerService kafkaConsumerService;

    public AiController(ChatClient.Builder chatClientBuilder,KafkaProducerService kafkaProducer,
    		KafkaConsumerService kafkaConsumerService) {
        this.chatClient = chatClientBuilder.build();
        this.kafkaProducer = kafkaProducer;
        this.kafkaConsumerService = kafkaConsumerService;
    }
    

    @GetMapping("/generate")
    public AiResponse generate(@RequestParam(value = "message") String message) {
    	System.out.println(">>> Richiesta ricevuta dal frontend! Domanda: " + message);
        AiResponse answer=  chatClient.prompt()
            .system("""
                    Sei un assistente tecnico avanzato. 
                    Devi rispondere ESCLUSIVAMENTE in lingua italiana. 
                    È tassativamente vietato l'uso di caratteri, ideogrammi o parole in cinese o in inglese.
                    Tutte le chiavi e i valori del JSON devono essere in italiano.
                    """)
            .user(u -> u.text("""
                    Spiegami in modo tecnico: {argomento}.
                    
                    REGOLE DI FORMATTAZIONE IMPERATIVE:
                    1. Restituisci ESCLUSIVAMENTE JSON grezzo valido.
                    2. NON usare la formattazione Markdown e NON usare i backtick.
                    3. Inizia la risposta direttamente con una parentesi graffa di apertura e terminala con una di chiusura.
                    """)
                    .param("argomento", message))
            // FIX 2: Rimosso il blocco ".options()", la temperatura ora è nel file application.properties!
            .call()
            // FIX 3: La classe ora viene riconosciuta correttamente grazie all'import giusto
            .entity(AiResponse.class);
        System.out.println("<<< Risposta generata da Qwen: " + answer);
        kafkaProducer.inviaRisposta(answer);
        return answer;
    }
    
    
    @GetMapping("/history")
    public List<String> getCronologiaKafka() {
        // Restituisce la lista di stringhe JSON salvate dal Consumer
        return kafkaConsumerService.getCronologia();
    }
}