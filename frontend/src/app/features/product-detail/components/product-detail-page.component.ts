import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';
import { Product, ProductVariant } from '../../../core/models/product.model';
import { CartService } from '../../../core/services/cart.service';
import { ProductService } from '../../../core/services/product.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-product-detail-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, LoadingSpinnerComponent],
  templateUrl: './product-detail-page.component.html',
  styleUrls: ['./product-detail-page.component.scss']
})
export class ProductDetailPageComponent implements OnInit {
  product: Product | null = null;
  selectedImage = '';
  selectedSize: number | null = null;
  selectedColor = '';
  quantity = 1;
  postalCode = '';
  isLoading = true;
  errorMessage = '';
  feedbackMessage = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly productService: ProductService,
    private readonly cartService: CartService
  ) {}

  ngOnInit(): void {
    this.route.paramMap.pipe(
      switchMap(params => this.productService.getProductBySlug(params.get('slug') ?? ''))
    ).subscribe({
      next: product => {
        this.product = product;
        this.selectedImage = product.imageUrl;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'Este produto não está disponível no momento.';
        this.isLoading = false;
      }
    });
  }

  selectSize(size: number): void {
    this.selectedSize = size;
    if (!this.colorsForSelectedSize().includes(this.selectedColor)) {
      this.selectedColor = '';
    }
    this.feedbackMessage = '';
  }

  colorsForSelectedSize(): string[] {
    if (!this.product?.variants || this.selectedSize == null) {
      return [];
    }
    return [...new Set(
      this.product.variants
        .filter(variant => variant.size === this.selectedSize && variant.availableStock > 0)
        .map(variant => variant.color)
    )].sort();
  }

  addToCart(): void {
    if (!this.product || this.selectedSize == null || !this.selectedColor) {
      this.feedbackMessage = 'Escolha o tamanho e a cor antes de adicionar à sacola.';
      return;
    }

    const variant = this.selectedVariant();
    if (!variant || variant.availableStock < this.quantity) {
      this.feedbackMessage = 'Essa combinação não possui estoque suficiente.';
      return;
    }

    this.cartService.addToCart(
      this.product,
      this.quantity,
      this.selectedSize,
      this.selectedColor,
      variant.id
    ).subscribe({
      next: () => {
        this.feedbackMessage = 'Produto adicionado à sua sacola.';
      },
      error: error => {
        this.feedbackMessage = error.error?.message ?? 'Não foi possível adicionar o produto.';
      }
    });
  }

  shippingPreview(): void {
    const normalized = this.postalCode.replace(/\D/g, '');
    this.feedbackMessage = normalized.length === 8
      ? 'Informe o endereço no checkout para ver as opções e prazos finais.'
      : 'Digite um CEP válido com 8 números.';
  }

  private selectedVariant(): ProductVariant | undefined {
    return this.product?.variants?.find(
      variant => variant.size === this.selectedSize
        && variant.color === this.selectedColor
        && variant.active
    );
  }
}
