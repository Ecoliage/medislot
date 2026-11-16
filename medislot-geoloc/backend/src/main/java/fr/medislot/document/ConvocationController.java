package fr.medislot.document;

import java.security.Principal;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConvocationController {

  private final ConvocationService service;

  public ConvocationController(ConvocationService service) {
    this.service = service;
  }

  @GetMapping(
      value = "/appointments/{id}/convocation.pdf",
      produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> convocation(
      Principal principal,
      @PathVariable Long id,
      @RequestParam(defaultValue = "false") boolean download) {
    byte[] pdf = service.build(principal.getName(), id);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .contentLength(pdf.length)
        .cacheControl(CacheControl.noStore())
        .header("Content-Disposition", disposition(service.filename(id), download).toString())
        .body(pdf);
  }

  private ContentDisposition disposition(String filename, boolean download) {
    ContentDisposition.Builder builder =
        download ? ContentDisposition.attachment() : ContentDisposition.inline();
    return builder.filename(filename).build();
  }
}
