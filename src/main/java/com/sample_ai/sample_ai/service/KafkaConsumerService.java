package com.sample_ai.sample_ai.service;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class KafkaConsumerService {

    // Questo è il nostro "Database" temporaneo in memoria per lo storico
    private final List<String> cronologiaRisposte = new ArrayList<>();

    // Questa annotazione dice a Spring: "Rimani sempre in ascolto su questo canale!"
    @KafkaListener(topics = "nuove-risposte-ai", groupId = "qwen-assistente-group")
    public void ascoltaNuovaRisposta(String messaggioRicevuto) {
        System.out.println("📥 [CONSUMER] Nuovo messaggio catturato da Kafka!");
        
        // Aggiungiamo il messaggio in cima alla nostra lista (storico)
        cronologiaRisposte.add(0, messaggioRicevuto);
        
        System.out.println("📊 Storico aggiornato. Risposte totali salvate: " + cronologiaRisposte.size());
    }

    // Metodo per permettere (in futuro) di leggere tutta la cronologia
    public List<String> getCronologia() {
        return cronologiaRisposte;
    }
}