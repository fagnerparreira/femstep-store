import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ProductService } from '../../../core/services/product.service';
import { Product } from '../../../core/models/product.model';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-catalog-page',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductCardComponent, LoadingSpinnerComponent],
  templateUrl: './catalog-page.component.html',
  styleUrls: ['./catalog-page.component.scss']
})
export class CatalogPageComponent implements OnInit {
  products: Product[] = [];
  isLoading: boolean = true;
  selectedCategory: string | null = null;
  searchTerm: string = '';

  constructor(
    private productService: ProductService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.loadProducts();
    this.handleQueryParams();
  }

  private loadProducts(): void {
    this.productService.getProducts().subscribe({
      next: (products) => {
        this.products = products;
        this.isLoading = false;
      },
      error: (error) => {
        console.error('Error loading products:', error);
        this.isLoading = false;
      }
    });
  }

  private handleQueryParams(): void {
    this.route.queryParams.subscribe(params => {
      this.selectedCategory = params['category'] || null;
      this.searchTerm = params['search'] || '';
      
      if (this.selectedCategory || this.searchTerm) {
        this.filterProducts();
      }
    });
  }

  private filterProducts(): void {
    this.isLoading = true;
    
    if (this.searchTerm) {
      this.productService.searchProducts(this.searchTerm).subscribe({
        next: (products) => {
          this.products = this.filterByCategory(products);
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error searching products:', error);
          this.isLoading = false;
        }
      });
    } else if (this.selectedCategory) {
      this.productService.getProductsByCategory(this.selectedCategory).subscribe({
        next: (products) => {
          this.products = products;
          this.isLoading = false;
        },
        error: (error) => {
          console.error('Error loading products by category:', error);
          this.isLoading = false;
        }
      });
    }
  }

  private filterByCategory(products: Product[]): Product[] {
    if (!this.selectedCategory) {
      return products;
    }
    return products.filter(product => product.categorySlug === this.selectedCategory);
  }

  onCategoryChange(category: string | null): void {
    this.selectedCategory = category;
    this.filterProducts();
  }

  onSearchSubmit(): void {
    if (this.searchTerm.trim()) {
      this.filterProducts();
    }
  }

  clearFilters(): void {
    this.selectedCategory = null;
    this.searchTerm = '';
    this.loadProducts();
  }

  getCategoryDisplayName(categorySlug: string): string {
    const categoryNames: { [key: string]: string } = {
      'saltos': 'Saltos',
      'sapatilhas': 'Sapatilhas',
      'botas': 'Botas',
      'sandalias': 'Sandálias'
    };
    return categoryNames[categorySlug] || categorySlug;
  }
}
