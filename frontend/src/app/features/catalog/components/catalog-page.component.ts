import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Category, Product } from '../../../core/models/product.model';
import { ProductService } from '../../../core/services/product.service';
import { LoadingSpinnerComponent } from '../../../shared/components/loading-spinner/loading-spinner.component';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';

@Component({
  selector: 'app-catalog-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, ProductCardComponent, LoadingSpinnerComponent],
  templateUrl: './catalog-page.component.html',
  styleUrls: ['./catalog-page.component.scss']
})
export class CatalogPageComponent implements OnInit {
  products: Product[] = [];
  categories: Category[] = [];
  isLoading = true;
  errorMessage = '';
  totalCount = 0;
  currentPage = 0;
  totalPages = 0;
  readonly pageSize = 12;

  selectedCategory = '';
  searchTerm = '';
  selectedSize: number | null = null;
  selectedColor = '';
  minPrice: number | null = null;
  maxPrice: number | null = null;
  sort = 'featured';

  readonly sizes = [33, 34, 35, 36, 37, 38, 39, 40];
  readonly colors = ['Preto', 'Caramelo'];

  constructor(
    private readonly productService: ProductService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.productService.getCategories().subscribe({
      next: categories => this.categories = categories,
      error: () => this.categories = []
    });

    this.route.queryParamMap.subscribe(params => {
      this.selectedCategory = params.get('category') ?? '';
      this.searchTerm = params.get('search') ?? '';
      this.sort = params.get('sort') ?? 'featured';
      this.selectedSize = this.parseNumber(params.get('size'));
      this.selectedColor = params.get('color') ?? '';
      this.minPrice = this.parseNumber(params.get('minPrice'));
      this.maxPrice = this.parseNumber(params.get('maxPrice'));
      this.currentPage = Math.max((this.parseNumber(params.get('page')) ?? 1) - 1, 0);
      this.loadProducts();
    });
  }

  applyFilters(): void {
    this.updateQuery({ page: 1 });
  }

  clearFilters(): void {
    void this.router.navigate(['/catalog']);
  }

  selectCategory(slug: string): void {
    this.selectedCategory = slug;
    this.updateQuery({ category: slug || null, page: 1 });
  }

  changePage(page: number): void {
    if (page < 0 || page >= this.totalPages || page === this.currentPage) {
      return;
    }
    this.updateQuery({ page: page + 1 });
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  categoryName(): string {
    return this.categories.find(category => category.slug === this.selectedCategory)?.name
      ?? 'Todos os sapatos';
  }

  private loadProducts(): void {
    this.isLoading = true;
    this.errorMessage = '';

    this.productService.getProductPage({
      category: this.selectedCategory || undefined,
      search: this.searchTerm || undefined,
      size: this.selectedSize ?? undefined,
      color: this.selectedColor || undefined,
      minPrice: this.minPrice ?? undefined,
      maxPrice: this.maxPrice ?? undefined,
      page: this.currentPage,
      pageSize: this.pageSize,
      sort: this.sort
    }).subscribe({
      next: result => {
        this.products = result.products;
        this.totalCount = result.totalCount;
        this.totalPages = result.totalPages;
        this.currentPage = result.currentPage;
        this.isLoading = false;
      },
      error: () => {
        this.products = [];
        this.totalCount = 0;
        this.totalPages = 0;
        this.errorMessage = 'Não foi possível carregar os produtos. Tente novamente em instantes.';
        this.isLoading = false;
      }
    });
  }

  private updateQuery(extra: Record<string, string | number | null>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        category: this.selectedCategory || null,
        search: this.searchTerm.trim() || null,
        size: this.selectedSize,
        color: this.selectedColor || null,
        minPrice: this.minPrice,
        maxPrice: this.maxPrice,
        sort: this.sort === 'featured' ? null : this.sort,
        ...extra
      },
      queryParamsHandling: 'merge'
    });
  }

  private parseNumber(value: string | null): number | null {
    if (!value) return null;
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
}
