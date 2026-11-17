const clearers = new Set<() => void>();

export function onClinicChange(clear: () => void): () => void {
  clearers.add(clear);
  return () => clearers.delete(clear);
}

export function clearTenantCaches(): void {
  clearers.forEach(clear => clear());
}
