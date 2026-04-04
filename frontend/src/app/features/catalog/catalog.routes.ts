import { Routes } from '@angular/router';
import { CatalogPageComponent } from './components/catalog-page.component';

export const catalogRoutes: Routes = [
  {
    path: '',
    component: CatalogPageComponent,
    title: 'FemStep Store - Catálogo'
  }
];
