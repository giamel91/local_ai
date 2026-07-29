package com.sample_ai.sample_ai.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sample_ai.sample_ai.response.AiResponse;
import com.sample_ai.sample_ai.service.AiService;
import com.sample_ai.sample_ai.service.KafkaConsumerService;
import com.sample_ai.sample_ai.service.KafkaProducerService;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "http://localhost:4200") // SBLOCCA LE CHIAMATE DA ANGULAR
public class AiController {

    private final KafkaConsumerService kafkaConsumerService;
    private final AiService aiService;
    
    @Autowired
    private VectorStore vectorStore;


    public AiController(ChatClient.Builder chatClientBuilder,
    		KafkaConsumerService kafkaConsumerService, AiService aiService) {
        this.kafkaConsumerService = kafkaConsumerService;
		this.aiService = aiService;
    }
    
    private List<String> splitWithOverlap(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) return chunks;
        if (overlap >= size) throw new IllegalArgumentException("overlap deve essere < size");

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            chunks.add(text.substring(start, end));
            if (end == text.length()) break;
            start = end - overlap;
        }
        return chunks;
    }

    @GetMapping("/generate")
    public AiResponse generate(@RequestParam(value = "message") String message) {
    	System.out.println(">>> Richiesta ricevuta dal frontend! Domanda: " + message);
        AiResponse answer=aiService.askAi(message);
        System.out.println("<<< Risposta generata da Qwen: " + answer);
        return answer;
    }
    
    
    @GetMapping("/history")
    public List<String> getCronologiaKafka() {
        // Restituisce la lista di stringhe JSON salvate dal Consumer
        return kafkaConsumerService.getCronologia();
    }
    
    
    @PostMapping("/ingest")
    public Map<String, Object> caricaDocumentoInMemoria(@RequestBody Map<String, String> payload) {
        String testoDaMemorizzare = payload.get("testo");

        if (testoDaMemorizzare == null || testoDaMemorizzare.isBlank()) {
            return Map.of("status", "Errore: il testo è vuoto!");
        }

        String source = payload.getOrDefault("source", "manuale");
        int chunkSize = 800;
        int overlap = 120;

        List<String> chunks = splitWithOverlap(testoDaMemorizzare, chunkSize, overlap);
        List<Document> documenti = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = Map.of(
                "source", source,
                "chunkIndex", i,
                "totalChunks", chunks.size()
            );
            documenti.add(new Document(chunks.get(i), metadata));
        }

        vectorStore.accept(documenti);

        return Map.of(
            "status", "Successo! Testo vettorizzato e salvato in ChromaDB.",
            "chunksSalvati", documenti.size(),
            "chunkSize", chunkSize,
            "overlap", overlap
        );
    }
    
    @GetMapping("/search")
    public List<Map<String, Object>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "4") int k) {

        return vectorStore.similaritySearch(SearchRequest.query(query).withTopK(k))
                .stream()
                .map(d -> Map.of(
                    "testo", d.getContent() , 
                    "metadata", d.getMetadata()
                ))
                .toList();
    }
}