// Matches backend/docs/01-architecture.md's port map. No environment files
// (dev/prod) yet - everything points at localhost, revisit once there's an
// actual deployment target.
export const API_CONFIG = {
  movie: 'http://localhost:8091/api',
  user: 'http://localhost:8092/api',
  rating: 'http://localhost:8093/api',
  recommendation: 'http://localhost:8094/api',
};
