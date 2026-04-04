import { Component, Input } from '@angular/core';
import { Product } from '../../../core/models/product.model';

@Component({
  selector: 'app-product-card',
  standalone: true,
  templateUrl: './product-card.component.html',
  styleUrls: ['./product-card.component.scss']
})
export class ProductCardComponent {
  @Input() product!: Product;
  @Input() showQuickActions: boolean = true;

  constructor() { }

  onAddToCart(): void {
    // TODO: Implement add to cart logic
    console.log('Adding to cart:', this.product.name);
  }

  onQuickView(): void {
    // TODO: Implement quick view logic
    console.log('Quick view:', this.product.name);
  }

  onImageError(event: Event): void {
    const img = event.target as HTMLImageElement;
    img.src = '/assets/images/product-placeholder.jpg';
  }
}
