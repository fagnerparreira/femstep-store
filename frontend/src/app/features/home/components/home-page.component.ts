import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ProductService } from '../../../core/services/product.service';
import { Product } from '../../../core/models/product.model';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-home-page',
  standalone: true,
  imports: [CommonModule, ProductCardComponent, LoadingSpinnerComponent],
  templateUrl: './home-page.component.html',
  styleUrls: ['./home-page.component.scss']
})
export class HomePageComponent implements OnInit {
  featuredProducts: Product[] = [];
  isLoading: boolean = true;

  constructor(private productService: ProductService) {}

  ngOnInit(): void {
    this.loadFeaturedProducts();
  }

  private loadFeaturedProducts(): void {
    this.productService.getFeaturedProducts().subscribe({
      next: (products) => {
        this.featuredProducts = products;
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading featured products:', error);
        this.isLoading = false;
      }
    });
  }
}
