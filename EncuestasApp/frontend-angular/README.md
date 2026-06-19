# Cliente Angular base — Votaciones API

Capa de conexión (no UI) lista para enchufar en un proyecto Angular 17+ standalone.
Centraliza la URL del backend, el token JWT, el manejo de errores 401 y las
llamadas a todos los endpoints. Encima de esto construyes tus componentes.

## Estructura

```
src/
├─ environments/
│  └─ environment.ts            # apiBaseUrl y wsUrl
└─ app/
   ├─ app.config.ts             # provideHttpClient + interceptor + rutas
   └─ core/
      ├─ models/api.models.ts   # tipos TS de los DTOs del backend
      ├─ auth/
      │  ├─ auth.service.ts      # login/registro/logout + señales (isAuthenticated, isAdmin)
      │  ├─ auth.interceptor.ts  # añade Bearer token; logout en 401
      │  └─ auth.guards.ts       # authGuard / adminGuard
      ├─ api/
      │  ├─ encuesta.service.ts  # CRUD + listados de encuestas
      │  └─ voto.service.ts      # votar + resultados
      └─ realtime/
         └─ realtime.service.ts  # resultados en vivo (WebSocket/STOMP, admin)
```

## Integración en un proyecto Angular existente

1. Copia la carpeta `src/app/core` y `src/environments/environment.ts` a tu proyecto.
2. Asegúrate de registrar el interceptor en tu `app.config.ts`:
   ```ts
   provideHttpClient(withInterceptors([authInterceptor]))
   ```
3. Ajusta `environment.ts` con la URL real del backend en producción.

## Dependencias para el tiempo real (solo si usas `RealtimeService`)

```bash
npm install @stomp/stompjs sockjs-client
npm install -D @types/sockjs-client
```

> **Polyfill SockJS:** sockjs-client espera la variable global `global`. Si ves
> `global is not defined`, añade en `src/polyfills.ts` (o al inicio de `main.ts`):
> ```ts
> (window as any).global = window;
> ```

## Ejemplos de uso

### Login (en un componente)
```ts
private auth = inject(AuthService);
private router = inject(Router);

onSubmit() {
  this.auth.login({ email, password }).subscribe({
    next: () => this.router.navigate(['/']),
    error: (e) => this.error = e.error?.mensaje ?? 'Error al iniciar sesión',
  });
}
```

### Estado de sesión reactivo (en plantilla)
```ts
isAdmin = inject(AuthService).isAdmin;       // signal
```
```html
@if (isAdmin()) { <a routerLink="/admin">Panel admin</a> }
```

### Listar y votar
```ts
private encuestas = inject(EncuestaService);
private votos = inject(VotoService);

ngOnInit() {
  this.encuestas.listarActivas().subscribe(list => this.activas = list);
}

votar(encuestaId: string, opcionId: string) {
  this.votos.votar(encuestaId, opcionId).subscribe(resultado => {
    this.resultado = resultado; // ya trae el desglose con porcentajes
  });
}
```

### Resultados en vivo (admin)
```ts
private realtime = inject(RealtimeService);
private stop?: () => void;

ngOnInit() {
  this.stop = this.realtime.watchResultados(this.encuestaId, r => this.resultado = r);
}
ngOnDestroy() {
  this.stop?.();
}
```

## Manejo de errores

Todas las respuestas de error siguen el formato `ApiError`
(`{ status, error, mensaje, timestamp, campos? }`). En los `error` de RxJS,
el cuerpo viene en `err.error`:
```ts
error: (err) => {
  const msg = err.error?.mensaje ?? 'Error inesperado';
  const campos = err.error?.campos; // map campo -> mensaje (validación 400)
}
```
El interceptor ya hace **logout + redirección a /login** automáticamente en los 401.

## Referencia completa

Ver `../FRONTEND.md` para endpoints, reglas de negocio, estados y formatos.
```
