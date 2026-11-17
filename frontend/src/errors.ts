export function errorMessage(error: unknown): string {
  return error instanceof TypeError
    ? 'Connexion impossible. Vérifiez le réseau puis réessayez.'
    : error instanceof Error
      ? error.message
      : 'Une erreur est survenue.';
}
