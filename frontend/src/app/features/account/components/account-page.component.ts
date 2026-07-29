import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-account-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './account-page.component.html',
  styleUrls: ['./account-page.component.scss']
})
export class AccountPageComponent {
  private readonly authService = inject(AuthService);
  readonly user$ = this.authService.currentUser$;
}
