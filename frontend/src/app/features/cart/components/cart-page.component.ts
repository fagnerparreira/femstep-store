import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Cart } from '../../../core/models/cart.model';
import { AuthService } from '../../../core/services/auth.service';
import { CartService } from '../../../core/services/cart.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-cart-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './cart-page.component.html',
  styleUrls: ['./cart-page.component.scss']
})
export class CartPageComponent implements OnInit {
  private readonly cartService = inject(CartService);
  readonly authService = inject(AuthService);
  readonly cart$ = this.cartService.cart$;
  isUpdating = false;
  errorMessage = '';

  ngOnInit(): void {
    this.cartService.refresh().subscribe({
      error: error => {
        this.errorMessage = error.error?.message ?? 'Não foi possível atualizar a sacola.';
      }
    });
  }

  changeQuantity(itemId: number, quantity: number): void {
    this.runUpdate(this.cartService.updateQuantity(itemId, quantity));
  }

  remove(itemId: number): void {
    this.runUpdate(this.cartService.removeFromCart(itemId));
  }

  clear(): void {
    this.runUpdate(this.cartService.clearCart());
  }

  private runUpdate(request$: Observable<Cart>): void {
    this.errorMessage = '';
    this.isUpdating = true;
    request$.subscribe({
      next: () => {
        this.isUpdating = false;
      },
      error: error => {
        this.errorMessage = error.error?.message ?? 'Não foi possível atualizar a sacola.';
        this.isUpdating = false;
      }
    });
  }
}
