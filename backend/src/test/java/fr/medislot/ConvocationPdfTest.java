package fr.medislot;

import static org.junit.jupiter.api.Assertions.*;

import fr.medislot.document.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class ConvocationPdfTest {
  private static byte[] pdf;
  private static String text;

  @BeforeAll
  static void render() throws Exception {
    var resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    var engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
    var view =
        new ConvocationView(
            "Hélène Durand",
            "lundi 12 janvier 2026",
            "9 h 30",
            "Dr Camille Lefèvre",
            "Médecine générale",
            "12 rue de la République",
            "Lyon",
            "RDV-42");
    pdf = new PdfRenderer(engine).render(view);
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      text = new PDFTextStripper().getText(doc);
    }
  }

  @Test
  void startsWithPdfSignature() {
    assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
  }

  @Test
  void containsPatientNameAndParisTime() {
    assertTrue(text.contains("Hélène Durand"), text);
    assertTrue(text.contains("9 h 30"), text);
  }

  @Test
  void embedsFontsOfFirstPage() throws Exception {
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      PDResources resources = doc.getPage(0).getResources();
      int count = 0;
      for (COSName name : resources.getFontNames()) {
        assertTrue(resources.getFont(name).isEmbedded(), name.getName());
        count++;
      }
      assertTrue(count > 0);
    }
  }

  @Test
  void writesAnExampleFile() throws Exception {
    Path file = Path.of("target", "convocation-exemple.pdf");
    Files.createDirectories(file.getParent());
    Files.write(file, pdf);
    assertTrue(Files.exists(file));
    assertTrue(Files.size(file) > 1000);
  }
}
