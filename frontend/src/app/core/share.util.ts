// "Share with friends" (audit requirement) doesn't need a friends/messaging model -
// there's no such relationship in the graph. A copy-to-clipboard deep link covers the
// requirement without inventing an in-app social feature that isn't asked for elsewhere.
export function buildMovieShareLink(movieId: string): string {
  return `${window.location.origin}/movies/${movieId}`;
}

export async function copyMovieShareLink(movieId: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(buildMovieShareLink(movieId));
    return true;
  } catch {
    return false;
  }
}
