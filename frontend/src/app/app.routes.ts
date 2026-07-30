import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/components/home-page.component').then(m => m.HomePageComponent),
    title: 'MOVAZU - Início'
  },
  {
    path: 'catalog',
    loadComponent: () => import('./features/catalog/components/catalog-page.component').then(m => m.CatalogPageComponent),
    title: 'MOVAZU - Catálogo'
  },
  {
    path: 'product/:slug',
    loadComponent: () => import('./features/product-detail/components/product-detail-page.component').then(m => m.ProductDetailPageComponent),
    title: 'MOVAZU - Produto'
  },
  {
    path: 'auth/login',
    loadComponent: () => import('./features/auth/components/auth-page.component').then(m => m.AuthPageComponent),
    data: { mode: 'login' },
    title: 'Entrar | MOVAZU'
  },
  {
    path: 'auth/register',
    loadComponent: () => import('./features/auth/components/auth-page.component').then(m => m.AuthPageComponent),
    data: { mode: 'register' },
    title: 'Criar conta | MOVAZU'
  },
  {
    path: 'auth/forgot-password',
    loadComponent: () => import('./features/auth/components/auth-page.component').then(m => m.AuthPageComponent),
    data: { mode: 'forgot' },
    title: 'Recuperar senha | MOVAZU'
  },
  {
    path: 'auth/reset-password',
    loadComponent: () => import('./features/auth/components/auth-page.component').then(m => m.AuthPageComponent),
    data: { mode: 'reset' },
    title: 'Redefinir senha | MOVAZU'
  },
  {
    path: 'auth/verify-email',
    loadComponent: () => import('./features/auth/components/auth-page.component').then(m => m.AuthPageComponent),
    data: { mode: 'verify' },
    title: 'Verificar e-mail | MOVAZU'
  },
  {
    path: 'account',
    loadComponent: () => import('./features/account/components/account-page.component').then(m => m.AccountPageComponent),
    canActivate: [authGuard],
    title: 'Minha conta | MOVAZU'
  },
  {
    path: 'cart',
    loadComponent: () => import('./features/cart/components/cart-page.component').then(m => m.CartPageComponent),
    title: 'Sacola | MOVAZU'
  },
  {
    path: 'checkout',
    loadComponent: () => import('./features/checkout/components/checkout-page.component').then(m => m.CheckoutPageComponent),
    canActivate: [authGuard],
    title: 'Checkout seguro | MOVAZU'
  },
  {
    path: 'account/orders',
    loadComponent: () => import('./features/orders/components/orders-page.component').then(m => m.OrdersPageComponent),
    canActivate: [authGuard],
    title: 'Meus pedidos | MOVAZU'
  },
  {
    path: 'account/orders/:orderNumber',
    loadComponent: () => import('./features/orders/components/order-detail-page.component').then(m => m.OrderDetailPageComponent),
    canActivate: [authGuard],
    title: 'Detalhes do pedido | MOVAZU'
  },
  {
    path: 'account/addresses',
    loadComponent: () => import('./features/addresses/components/addresses-page.component').then(m => m.AddressesPageComponent),
    canActivate: [authGuard],
    title: 'Meus endereços | MOVAZU'
  },
  {
    path: 'favorites',
    loadComponent: () => import('./features/favorites/components/favorites-page.component').then(m => m.FavoritesPageComponent),
    canActivate: [authGuard],
    title: 'Meus favoritos | MOVAZU'
  },
  {
    path: 'admin',
    loadComponent: () => import('./features/admin/components/admin-page.component').then(m => m.AdminPageComponent),
    canActivate: [adminGuard],
    title: 'Painel administrativo | MOVAZU'
  },
  // {
  //   path: 'auth',
  //   loadChildren: () => import('./features/auth/auth.routes').then(m => m.authRoutes)
  // },
  // {
  //   path: 'profile',
  //   loadComponent: () => import('./features/profile/components/profile-page.component').then(m => m.ProfilePageComponent),
  //   canActivate: [authGuard],
  //   title: 'MOVAZU - Perfil'
  // },
  // {
  //   path: 'orders',
  //   loadComponent: () => import('./features/orders/components/orders-page.component').then(m => m.OrdersPageComponent),
  //   canActivate: [authGuard],
  //   title: 'MOVAZU - Pedidos'
  // },
  // {
  //   path: 'admin',
  //   loadChildren: () => import('./features/admin/admin.routes').then(m => m.adminRoutes),
  //   canActivate: [authGuard],
  //   title: 'MOVAZU - Admin'
  // },
  {
    path: '**',
    redirectTo: '',
    pathMatch: 'full'
  }
];
