package fr.medislot.document;

import com.openhtmltopdf.extend.FSSupplier;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

@Component
public class PdfRenderer {

  private static final String FAMILY = "Liberation Sans";
  private static final Resource REGULAR = new ClassPathResource("fonts/LiberationSans-Regular.ttf");
  private static final Resource BOLD = new ClassPathResource("fonts/LiberationSans-Bold.ttf");

  private final ITemplateEngine templates;

  public PdfRenderer(ITemplateEngine templates) {
    this.templates = templates;
  }

  public byte[] render(ConvocationView view) {
    try {
      return toPdf(html(view));
    } catch (Exception cause) {
      throw new RenderingFailed(cause);
    }
  }

  private String html(ConvocationView view) {
    Context context = new Context();
    context.setVariable("c", view);
    return templates.process("convocation", context);
  }

  private byte[] toPdf(String html) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PdfRendererBuilder builder = new PdfRendererBuilder();
    builder.useFastMode();
    builder.usePdfUaAccessibility(true);
    // Polices embarquées : sans elles les accents sortent mal selon le lecteur PDF.
    font(builder, REGULAR, 400);
    font(builder, BOLD, 700);
    builder.withHtmlContent(html, null);
    builder.toStream(out);
    builder.run();
    return out.toByteArray();
  }

  private void font(PdfRendererBuilder builder, Resource resource, int weight) {
    builder.useFont(
        (FSSupplier<InputStream>) () -> open(resource), FAMILY, weight, FontStyle.NORMAL, true);
  }

  private static InputStream open(Resource resource) {
    try {
      return resource.getInputStream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
