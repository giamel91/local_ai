package com.sample_ai.sample_ai.service;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import com.sample_ai.sample_ai.response.AiResponse;


@Service
public class AiService {
	
    private final ChatClient chatClient;


    public AiService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();

    }
    
	
	public AiResponse  askAi(String message) {
		
		AiResponse answer = chatClient.prompt()
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
		return answer;
		
		
		
	}

}
