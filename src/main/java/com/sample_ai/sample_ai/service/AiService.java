package com.sample_ai.sample_ai.service;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.sample_ai.sample_ai.response.AiResponse;


@Service
public class AiService {
	
    private final ChatClient chatClient;
    private final KafkaProducerService kafkaProducer;
    private final VectorStore vectorStore;




    public AiService(ChatClient.Builder chatClientBuilder,KafkaProducerService kafkaProducer, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.kafkaProducer = kafkaProducer;
        this.vectorStore= vectorStore;


    }
    
	
	public AiResponse  askAi(String message) {
		
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.query(message).withTopK(4)
        );
        
        List<Document> docsSafe = docs != null ? docs : Collections.emptyList();

        String context = docsSafe.stream()
                .map(Document::getContent)   // su M1 quasi sempre questo
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining("\n\n---\n\n"));
        
   
		AiResponse answer = chatClient.prompt()
        .system("""
        		Sei un assistente tecnico avanzato.
                Rispondi solo in italiano.
                Usa prima il contesto recuperato; se non basta, dillo chiaramente.
                Restituisci solo JSON valido.
                """)
        .user(u -> u.text("""
                CONTESTO:
                {context}

                DOMANDA:
                {argomento}
                """)
                .param("context", context)
        		.param("argomento", message))

        // FIX 2: Rimosso il blocco ".options()", la temperatura ora è nel file application.properties!
        .call()
        // FIX 3: La classe ora viene riconosciuta correttamente grazie all'import giusto
        .entity(AiResponse.class);
        kafkaProducer.inviaRisposta(answer);

		return answer;
		
		
		
	}

}
