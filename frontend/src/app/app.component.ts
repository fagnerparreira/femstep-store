import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MainLayoutComponent } from './layout/components/main-layout/main-layout.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, MainLayoutComponent],
  template: `
    <app-main-layout></app-main-layout>
  `,
  styles: []
})
export class AppComponent {
  title = 'FemStep Store';
}
