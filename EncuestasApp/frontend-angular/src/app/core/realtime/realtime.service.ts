import { inject, Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { environment } from '../../../environments/environment';
import { AuthService } from '../auth/auth.service';
import { ResultadoEncuesta } from '../models/api.models';

/**
 * Resultados en tiempo real por WebSocket/STOMP. Reservado a administradores:
 * el backend valida el JWT en el CONNECT y solo autoriza la suscripción a
 * /topic/admin/encuesta/{id} a usuarios con rol admin.
 *
 * Requiere las dependencias: @stomp/stompjs y sockjs-client.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {

  private readonly auth = inject(AuthService);

  /**
   * Abre una conexión STOMP y se suscribe a los resultados en vivo de una
   * encuesta. Devuelve una función para cerrar la suscripción y la conexión.
   *
   *   const stop = realtime.watchResultados(id, r => this.resultado = r);
   *   // ...al destruir el componente:
   *   stop();
   */
  watchResultados(
    encuestaId: string,
    onResultado: (r: ResultadoEncuesta) => void,
    onError?: (e: unknown) => void,
  ): () => void {
    const client = new Client({
      webSocketFactory: () => new SockJS(environment.wsUrl),
      connectHeaders: { Authorization: `Bearer ${this.auth.token() ?? ''}` },
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/admin/encuesta/${encuestaId}`, (msg: IMessage) => {
          onResultado(JSON.parse(msg.body) as ResultadoEncuesta);
        });
      },
      onStompError: (frame) => onError?.(frame),
      onWebSocketError: (evt) => onError?.(evt),
    });

    client.activate();
    return () => void client.deactivate();
  }
}
