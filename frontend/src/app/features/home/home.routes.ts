import { Routes } from '@angular/router';
import { HomePageComponent } from './components/home-page.component';

export const homeRoutes: Routes = [
  {
    path: '',
    component: HomePageComponent,
    title: 'FemStep Store - Início'
  }
];
