/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend origin for separately hosted builds, e.g. https://api.example.com. Empty means same origin. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
