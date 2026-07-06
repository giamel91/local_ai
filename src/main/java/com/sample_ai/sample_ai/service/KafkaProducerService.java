package com.sample_ai.sample_ai.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class KafkaProducerService {

    // Strumento di Spring per inviare messaggi a Kafka
    private final KafkaTemplate<String, String> kafkaTemplate;
    // Strumento per convertire oggetti Java in testo JSON
    private final ObjectMapper objectMapper; 

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void inviaRisposta(Object rispostaAi) {
        try {
            // 1. Trasforma l'oggetto in una stringa JSON
            String jsonRisposta = objectMapper.writeValueAsString(rispostaAi);
            
            // 2. Invia il messaggio sul "canale" di Kafka
            kafkaTemplate.send("nuove-risposte-ai", jsonRisposta);
            
            System.out.println("🚀 [PRODUCER] Risposta inviata con successo a Kafka!");
        } catch (Exception e) {
            System.err.println("Errore nell'invio a Kafka: " + e.getMessage());
        }
    }
}