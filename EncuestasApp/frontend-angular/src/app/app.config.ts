import { ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, Routes } from '@angular/router';
import { authInterceptor } from './core/auth/auth.interceptor';

/**
 * Define aquí tus rutas. Ejemplo usando los guards incluidos:
 *
 *   import { authGuard, adminGuard } from './core/auth/auth.guards';
 *
 *   export const routes: Routes = [
 *     { path: 'login', loadComponent: () => import('./pages/login/login.component') },
 *     { path: '', canActivate: [authGuard], loadComponent: () => import('./pages/home/home.component') },
 *     { path: 'admin', canActivate: [adminGuard], loadComponent: () => import('./pages/admin/admin.component') },
 *   ];
 */
export const routes: Routes = [];

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    // Registra el interceptor que añade el token y maneja el 401.
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
