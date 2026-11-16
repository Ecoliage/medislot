package fr.medislot;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import fr.medislot.api.Errors;
import fr.medislot.document.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ConvocationControllerTest {
  private final ConvocationService service = mock(ConvocationService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new ConvocationController(service))
          .setControllerAdvice(new Errors())
          .build();
  private final byte[] pdf = "%PDF-1.7 convocation".getBytes();

  ConvocationControllerTest() {
    when(service.filename(7L)).thenReturn("convocation-7.pdf");
  }

  @Test
  void servesThePdfInlineByDefault() throws Exception {
    when(service.build("patient@test", 7L)).thenReturn(pdf);
    mvc.perform(get("/api/appointments/7/convocation.pdf").principal(() -> "patient@test"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Content-Disposition", containsString("inline")))
        .andExpect(
            header()
                .string("Content-Disposition", containsString("filename=\"convocation-7.pdf\"")))
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().longValue("Content-Length", pdf.length))
        .andExpect(content().bytes(pdf));
  }

  @Test
  void downloadParameterServesAnAttachment() throws Exception {
    when(service.build("patient@test", 7L)).thenReturn(pdf);
    mvc.perform(
            get("/api/appointments/7/convocation.pdf")
                .param("download", "true")
                .principal(() -> "patient@test"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(
            header()
                .string("Content-Disposition", containsString("filename=\"convocation-7.pdf\"")));
  }

  @Test
  void unknownAppointmentIsAProblemNotFound() throws Exception {
    when(service.build("patient@test", 7L))
        .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, ConvocationService.NOT_FOUND));
    mvc.perform(get("/api/appointments/7/convocation.pdf").principal(() -> "patient@test"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail").value("Rendez-vous introuvable."));
  }

  @Test
  void cancelledAppointmentIsAProblemConflict() throws Exception {
    when(service.build("patient@test", 7L))
        .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, ConvocationService.CANCELLED));
    mvc.perform(get("/api/appointments/7/convocation.pdf").principal(() -> "patient@test"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.detail").value(ConvocationService.CANCELLED));
  }
}
