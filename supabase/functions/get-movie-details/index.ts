import { withSupabase } from "npm:@supabase/server@^1";

const TMDB_BASE_URL = "https://api.themoviedb.org/3";

type MovieDetailsRequest = {
  movie_id?: number | string;
};

type MovieDetailsResponse = {
  id: number;
  title: string | null;
  overview: string | null;
  tagline: string | null;
  release_date: string | null;
  release_year: number | null;
  runtime_minutes: number | null;
  vote_average: number | null;
  vote_count: number | null;
  genres: string[];
  poster_path: string | null;
  backdrop_path: string | null;
  imdb_id: string | null;
  status: string | null;
  original_language: string | null;
};

function jsonResponse(body: unknown, status = 200): Response {
  return Response.json(body, { status });
}

function errorResponse(
  error: string,
  status: number,
  details?: string,
): Response {
  return jsonResponse(
    {
      error,
      ...(details ? { details } : {}),
    },
    status,
  );
}

function parseMovieId(value: unknown): number | null {
  if (typeof value === "number" && Number.isInteger(value) && value > 0) {
    return value;
  }
  if (typeof value === "string") {
    const trimmed = value.trim();
    if (/^\d+$/.test(trimmed)) {
      const parsed = Number.parseInt(trimmed, 10);
      return parsed > 0 ? parsed : null;
    }
  }
  return null;
}

function parseReleaseYear(releaseDate: string | null | undefined): number | null {
  if (!releaseDate || releaseDate.length < 4) return null;
  const year = Number.parseInt(releaseDate.slice(0, 4), 10);
  return Number.isFinite(year) && year >= 1800 && year <= 2200 ? year : null;
}

function mapTmdbMovie(payload: Record<string, unknown>): MovieDetailsResponse {
  const genres = Array.isArray(payload.genres)
    ? payload.genres
      .map((entry) => {
        if (entry && typeof entry === "object" && "name" in entry) {
          const name = (entry as { name?: unknown }).name;
          return typeof name === "string" ? name.trim() : "";
        }
        return "";
      })
      .filter((name) => name.length > 0)
    : [];

  const externalIds = payload.external_ids;
  const imdbId = externalIds &&
      typeof externalIds === "object" &&
      "imdb_id" in externalIds
    ? (externalIds as { imdb_id?: unknown }).imdb_id
    : null;

  const runtime = typeof payload.runtime === "number" && payload.runtime > 0
    ? payload.runtime
    : null;
  const voteAverage = typeof payload.vote_average === "number" &&
      payload.vote_average > 0
    ? payload.vote_average
    : null;
  const voteCount = typeof payload.vote_count === "number" &&
      payload.vote_count > 0
    ? payload.vote_count
    : null;

  const releaseDate = typeof payload.release_date === "string" &&
      payload.release_date.trim().length > 0
    ? payload.release_date.trim()
    : null;

  return {
    id: typeof payload.id === "number" ? payload.id : 0,
    title: typeof payload.title === "string" ? payload.title : null,
    overview: typeof payload.overview === "string" && payload.overview.trim().length > 0
      ? payload.overview.trim()
      : null,
    tagline: typeof payload.tagline === "string" && payload.tagline.trim().length > 0
      ? payload.tagline.trim()
      : null,
    release_date: releaseDate,
    release_year: parseReleaseYear(releaseDate),
    runtime_minutes: runtime,
    vote_average: voteAverage,
    vote_count: voteCount,
    genres,
    poster_path: typeof payload.poster_path === "string" &&
        payload.poster_path.trim().length > 0
      ? payload.poster_path.trim()
      : null,
    backdrop_path: typeof payload.backdrop_path === "string" &&
        payload.backdrop_path.trim().length > 0
      ? payload.backdrop_path.trim()
      : null,
    imdb_id: typeof imdbId === "string" && imdbId.trim().length > 0
      ? imdbId.trim()
      : null,
    status: typeof payload.status === "string" && payload.status.trim().length > 0
      ? payload.status.trim()
      : null,
    original_language: typeof payload.original_language === "string" &&
        payload.original_language.trim().length > 0
      ? payload.original_language.trim()
      : null,
  };
}

async function fetchTmdbMovieDetails(movieId: number): Promise<Response> {
  const apiKey = Deno.env.get("TMDB_API_KEY");
  if (!apiKey || apiKey.trim().length === 0) {
    return errorResponse(
      "server_misconfigured",
      500,
      "TMDB_API_KEY is not configured for this project.",
    );
  }

  const url = new URL(`${TMDB_BASE_URL}/movie/${movieId}`);
  url.searchParams.set("api_key", apiKey.trim());
  url.searchParams.set("append_to_response", "external_ids");

  let tmdbResponse: Response;
  try {
    tmdbResponse = await fetch(url.toString(), {
      method: "GET",
      headers: {
        Accept: "application/json",
      },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown fetch error";
    return errorResponse(
      "tmdb_unreachable",
      502,
      message,
    );
  }

  const rawBody = await tmdbResponse.text();
  let payload: Record<string, unknown> | null = null;
  if (rawBody.length > 0) {
    try {
      payload = JSON.parse(rawBody) as Record<string, unknown>;
    } catch {
      payload = null;
    }
  }

  if (!tmdbResponse.ok) {
    const tmdbMessage = payload && typeof payload.status_message === "string"
      ? payload.status_message
      : rawBody.slice(0, 240) || tmdbResponse.statusText;

    if (tmdbResponse.status === 404) {
      return errorResponse("movie_not_found", 404, tmdbMessage);
    }

    return errorResponse(
      "tmdb_request_failed",
      tmdbResponse.status >= 500 ? 502 : tmdbResponse.status,
      tmdbMessage,
    );
  }

  if (!payload || typeof payload.id !== "number") {
    return errorResponse(
      "invalid_tmdb_response",
      502,
      "TMDB returned a successful response without movie details.",
    );
  }

  return jsonResponse(mapTmdbMovie(payload));
}

export default {
  fetch: withSupabase(
    { auth: ["user", "publishable"] },
    async (req, _ctx) => {
      if (req.method !== "POST") {
        return errorResponse(
          "method_not_allowed",
          405,
          "Use POST with a JSON body: { \"movie_id\": 123 }",
        );
      }

      let body: MovieDetailsRequest;
      try {
        body = await req.json() as MovieDetailsRequest;
      } catch {
        return errorResponse(
          "invalid_json",
          400,
          "Request body must be valid JSON.",
        );
      }

      const movieId = parseMovieId(body.movie_id);
      if (movieId == null) {
        return errorResponse(
          "invalid_movie_id",
          400,
          "Provide a positive integer movie_id in the request body.",
        );
      }

      return fetchTmdbMovieDetails(movieId);
    },
  ),
};
