import { setOptions, importLibrary } from '@googlemaps/js-api-loader';

let isOptionsSet = false;
let googleMapsPromise: Promise<typeof google.maps | null> | null = null;
let googleAuthFailed = false;
const authFailureListeners = new Set<() => void>();

if (typeof window !== 'undefined') {
  // Capture Google Maps auth failure globally
  const prevAuthFailure = (window as unknown as { gm_authFailure?: () => void }).gm_authFailure;
  (window as unknown as { gm_authFailure?: () => void }).gm_authFailure = () => {
    console.warn('Google Maps JS API authentication failed (invalid key or unauthorized origin). Seamlessly falling back to OpenStreetMap / Leaflet.');
    googleAuthFailed = true;
    googleMapsPromise = null;
    authFailureListeners.forEach((listener) => {
      try {
        listener();
      } catch (err) {
        console.error(err);
      }
    });
    if (typeof prevAuthFailure === 'function') {
      try {
        prevAuthFailure();
      } catch {
        // ignore
      }
    }
  };
}

export const getGoogleMapsJsApiKey = (): string => {
  const env = import.meta.env;
  const key = (
    env.VITE_GOOGLE_MAPS_JS_API_KEY ||
    env.VITE_GOOGLE_MAPS_API_KEY ||
    ''
  ).trim();
  return key;
};

export const isGoogleMapsConfigured = (): boolean => {
  return Boolean(getGoogleMapsJsApiKey()) && !googleAuthFailed;
};

export const isGoogleMapsAuthFailed = (): boolean => {
  return googleAuthFailed;
};

export const onGoogleMapsAuthFailure = (callback: () => void): (() => void) => {
  authFailureListeners.add(callback);
  if (googleAuthFailed) {
    callback();
  }
  return () => {
    authFailureListeners.delete(callback);
  };
};

export async function loadGoogleMaps(
  libraries: Array<'maps' | 'core' | 'places' | 'geometry' | 'drawing' | 'marker'> = ['maps', 'core', 'places', 'geometry', 'marker']
): Promise<typeof google.maps | null> {
  const apiKey = getGoogleMapsJsApiKey();
  if (!apiKey || googleAuthFailed) {
    return null;
  }

  if (typeof window !== 'undefined' && window.google?.maps && !googleAuthFailed) {
    return window.google.maps;
  }

  if (googleMapsPromise) {
    return googleMapsPromise;
  }

  if (!isOptionsSet) {
    try {
      setOptions({
        key: apiKey,
        v: 'weekly',
      });
      isOptionsSet = true;
    } catch {
      // Options already set or error
    }
  }

  googleMapsPromise = (async () => {
    try {
      const mapsLib = await importLibrary('maps');
      if (window.google?.maps && mapsLib) {
        Object.assign(window.google.maps, mapsLib);
      }
      for (const lib of libraries) {
        if (lib !== 'maps') {
          try {
            const loadedLib = await importLibrary(lib);
            if (window.google?.maps && loadedLib) {
              Object.assign(window.google.maps, loadedLib);
            }
          } catch (libErr) {
            console.warn(`Optional Google Maps library "${lib}" skipped:`, libErr);
          }
        }
      }
      if (googleAuthFailed) {
        return null;
      }
      return window.google ? window.google.maps : null;
    } catch (err: unknown) {
      console.warn('Google Maps JS API failed to load, falling back to OSM/Leaflet:', err);
      googleAuthFailed = true;
      googleMapsPromise = null;
      authFailureListeners.forEach((listener) => {
        try {
          listener();
        } catch {
          // ignore
        }
      });
      return null;
    }
  })();

  return googleMapsPromise;
}

