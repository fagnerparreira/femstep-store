import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/components/home-page.component').then(m => m.HomePageComponent),
    title: 'FemStep Store - Início'
  },
  {
    path: 'catalog',
    loadComponent: () => import('./features/catalog/components/catalog-page.component').then(m => m.CatalogPageComponent),
    title: 'FemStep Store - Catálogo'
  },
  // TODO: Implementar estas features futuramente
  // {
  //   path: 'product/:id',
  //   loadComponent: () => import('./features/product-detail/components/product-detail-page.component').then(m => m.ProductDetailPageComponent),
  //   title: 'FemStep Store - Produto'
  // },
  // {
  //   path: 'cart',
  //   loadComponent: () => import('./features/cart/components/cart-page.component').then(m => m.CartPageComponent),
  //   title: 'FemStep Store - Carrinho'
  // },
  // {
  //   path: 'auth',
  //   loadChildren: () => import('./features/auth/auth.routes').then(m => m.authRoutes)
  // },
  // {
  //   path: 'profile',
  //   loadComponent: () => import('./features/profile/components/profile-page.component').then(m => m.ProfilePageComponent),
  //   canActivate: [authGuard],
  //   title: 'FemStep Store - Perfil'
  // },
  // {
  //   path: 'orders',
  //   loadComponent: () => import('./features/orders/components/orders-page.component').then(m => m.OrdersPageComponent),
  //   canActivate: [authGuard],
  //   title: 'FemStep Store - Pedidos'
  // },
  // {
  //   path: 'admin',
  //   loadChildren: () => import('./features/admin/admin.routes').then(m => m.adminRoutes),
  //   canActivate: [authGuard],
  //   title: 'FemStep Store - Admin'
  // },
  {
    path: '**',
    redirectTo: '',
    pathMatch: 'full'
  }
];
