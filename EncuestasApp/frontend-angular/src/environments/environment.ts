/** Configuración central del frontend. Cambiar la URL en producción. */
export const environment = {
  production: false,
  /** Base de la API REST. */
  apiBaseUrl: 'http://localhost:8080',
  /** Endpoint del handshake WebSocket (SockJS). */
  wsUrl: 'http://localhost:8080/ws',
};
