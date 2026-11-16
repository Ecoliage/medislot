package fr.medislot.application;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class SmsGateway {
  private final RestClient client;

  public SmsGateway(@Value("${medislot.sms.url}") String url) {
    client = RestClient.create(url);
  }

  public void envoyerConfirmation(Long patientId, String code) {
    client
        .post()
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "patient",
                patientId,
                "message",
                "Votre rendez-vous MediSlot est confirmé. Code : " + code))
        .retrieve()
        .toBodilessEntity();
  }
}
