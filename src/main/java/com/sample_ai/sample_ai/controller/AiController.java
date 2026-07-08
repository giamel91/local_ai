package com.sample_ai.sample_ai.controller;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
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

    private final KafkaProducerService kafkaProducer;
    private final KafkaConsumerService kafkaConsumerService;
    private final AiService aiService;


    public AiController(ChatClient.Builder chatClientBuilder,KafkaProducerService kafkaProducer,
    		KafkaConsumerService kafkaConsumerService, AiService aiService) {
        this.kafkaProducer = kafkaProducer;
        this.kafkaConsumerService = kafkaConsumerService;
		this.aiService = aiService;
    }
    

    @GetMapping("/generate")
    public AiResponse generate(@RequestParam(value = "message") String message) {
    	System.out.println(">>> Richiesta ricevuta dal frontend! Domanda: " + message);
        AiResponse answer=aiService.askAi(message);
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