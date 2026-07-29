import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Product } from '../../../core/models/product.model';
import { FavoriteService } from '../../../core/services/favorite.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';

@Component({
  selector: 'app-favorites-page',
  standalone: true,
  imports: [CommonModule, RouterLink, LoadingSpinnerComponent, ProductCardComponent],
  templateUrl: './favorites-page.component.html',
  styleUrls: ['./favorites-page.component.scss']
})
export class FavoritesPageComponent implements OnInit {
  products: Product[] = [];
  isLoading = true;
  errorMessage = '';

  constructor(private readonly favoriteService: FavoriteService) {}

  ngOnInit(): void {
    this.load();
  }

  remove(product: Product): void {
    this.favoriteService.remove(product.id).subscribe({
      next: () => this.products = this.products.filter(item => item.id !== product.id),
      error: () => this.errorMessage = 'Não foi possível remover o produto.'
    });
  }

  private load(): void {
    this.favoriteService.list().subscribe({
      next: products => {
        this.products = products;
        this.isLoading = false;
      },
      error: () => {
        this.errorMessage = 'Não foi possível carregar seus favoritos.';
        this.isLoading = false;
      }
    });
  }
}
