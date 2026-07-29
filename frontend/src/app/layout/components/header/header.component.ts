import { AsyncPipe, NgIf } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { CartService } from '../../../core/services/cart.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [AsyncPipe, FormsModule, NgIf, RouterLink, RouterLinkActive],
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent {
  searchTerm = '';
  readonly cart$;
  readonly currentUser$;

  constructor(
    private readonly router: Router,
    private readonly cartService: CartService,
    private readonly authService: AuthService
  ) {
    this.cart$ = this.cartService.cart$;
    this.currentUser$ = this.authService.currentUser$;
  }

  submitSearch(): void {
    const query = this.searchTerm.trim();
    if (!query) {
      return;
    }

    void this.router.navigate(['/catalog'], { queryParams: { search: query } });
  }

  logout(): void {
    this.authService.logout();
    void this.router.navigate(['/']);
  }
}
