package fr.medislot.document;

public class RenderingFailed extends RuntimeException {

  public RenderingFailed(Throwable cause) {
    super("Le rendu du PDF a échoué.", cause);
  }
}
