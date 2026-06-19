import { ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, Routes, withComponentInputBinding } from '@angular/router';
import { authInterceptor } from './core/auth/auth.interceptor';
import { authGuard, adminGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  // Públicas
  { path: 'login',    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent) },
  { path: 'registro', loadComponent: () => import('./pages/registro/registro.component').then(m => m.RegistroComponent) },

  // Usuario autenticado
  { path: '',           canActivate: [authGuard], loadComponent: () => import('./pages/home/home.component').then(m => m.HomeComponent) },
  { path: 'finalizadas', canActivate: [authGuard], loadComponent: () => import('./pages/finalizadas/finalizadas.component').then(m => m.FinalizadasComponent) },
  { path: 'encuesta/:id', canActivate: [authGuard], loadComponent: () => import('./pages/encuesta-detalle/encuesta-detalle.component').then(m => m.EncuestaDetalleComponent) },

  // Admin
  { path: 'admin',               canActivate: [adminGuard], loadComponent: () => import('./pages/admin/admin-panel.component').then(m => m.AdminPanelComponent) },
  { path: 'admin/encuesta/:id',  canActivate: [adminGuard], loadComponent: () => import('./pages/admin/admin-encuesta.component').then(m => m.AdminEncuestaComponent) },

  // Fallback
  { path: '**', redirectTo: '' },
];

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes, withComponentInputBinding()),
    provideHttpClient(withInterceptors([authInterceptor])),
  ],
};
