import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { Product } from '../../../core/models/product.model';
import { CartService } from '../../../core/services/cart.service';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './product-card.component.html',
  styleUrls: ['./product-card.component.scss']
})
export class ProductCardComponent {
  @Input() product!: Product;
  @Input() showQuickActions: boolean = true;

  constructor(private readonly cartService: CartService) { }

  onAddToCart(): void {
    const size = this.product.availableSizes.at(0);
    const color = this.product.availableColors.at(0);
    this.cartService.addToCart(this.product, 1, size, color);
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src = '/assets/images/product-placeholder.svg';
  }
}
