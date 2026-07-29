import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map, Observable, shareReplay } from 'rxjs';
import {
  Category,
  Product,
  ProductFilter,
  ProductImage,
  ProductSearchResult,
  ProductVariant
} from '../models/product.model';

export interface ProductSummaryApi {
  id: number;
  name: string;
  slug: string;
  shortDescription?: string;
  price: number;
  compareAtPrice?: number;
  imageUrl?: string;
  imageAlt?: string;
  category: string;
  categorySlug: string;
  material?: string;
  featured: boolean;
  onSale: boolean;
  availableSizes: number[];
  availableColors: string[];
  availableStock: number;
}

interface ProductDetailApi {
  id: number;
  name: string;
  slug: string;
  description: string;
  shortDescription?: string;
  price: number;
  compareAtPrice?: number;
  brand?: string;
  material?: string;
  category: string;
  categorySlug: string;
  featured: boolean;
  variants: ProductVariant[];
  images: ProductImage[];
}

interface PageApi<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ProductQuery extends ProductFilter {
  page?: number;
  pageSize?: number;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly apiUrl = '/api/public';
  private readonly http = inject(HttpClient);
  private readonly categories$ = this.http
    .get<Category[]>(`${this.apiUrl}/categories`)
    .pipe(shareReplay({ bufferSize: 1, refCount: true }));

  getCategories(): Observable<Category[]> {
    return this.categories$;
  }

  getProductPage(query: ProductQuery = {}): Observable<ProductSearchResult> {
    let params = new HttpParams()
      .set('page', query.page ?? 0)
      .set('size', query.pageSize ?? 12)
      .set('sort', query.sort ?? 'featured')
      .set('inStock', query.active ?? true);

    if (query.search) params = params.set('q', query.search);
    if (query.category) params = params.set('category', query.category);
    if (query.minPrice != null) params = params.set('minPrice', query.minPrice);
    if (query.maxPrice != null) params = params.set('maxPrice', query.maxPrice);
    if (query.size != null) params = params.append('sizes', query.size);
    if (query.color) params = params.append('colors', query.color);
    if (query.featured != null) params = params.set('featured', query.featured);

    return this.http
      .get<PageApi<ProductSummaryApi>>(`${this.apiUrl}/products`, { params })
      .pipe(
        map(result => ({
          products: result.content.map(product => this.fromSummary(product)),
          totalCount: result.totalElements,
          currentPage: result.page,
          totalPages: result.totalPages,
          pageSize: result.size,
          first: result.first,
          last: result.last
        }))
      );
  }

  getProducts(filters: ProductFilter = {}): Observable<Product[]> {
    return this.getProductPage({ ...filters, pageSize: 48 }).pipe(map(result => result.products));
  }

  getFeaturedProducts(): Observable<Product[]> {
    return this.http
      .get<ProductSummaryApi[]>(`${this.apiUrl}/products/featured`, {
        params: new HttpParams().set('limit', 8)
      })
      .pipe(map(products => products.map(product => this.fromSummary(product))));
  }

  getProductsByCategory(categorySlug: string): Observable<Product[]> {
    return this.getProducts({ category: categorySlug });
  }

  searchProducts(query: string): Observable<Product[]> {
    return this.getProducts({ search: query });
  }

  getProductBySlug(slug: string): Observable<Product> {
    return this.http
      .get<ProductDetailApi>(`${this.apiUrl}/products/${encodeURIComponent(slug)}`)
      .pipe(map(product => this.fromDetail(product)));
  }

  fromSummary(product: ProductSummaryApi): Product {
    return {
      id: product.id,
      name: product.name,
      slug: product.slug,
      shortDescription: product.shortDescription,
      price: product.price,
      originalPrice: product.compareAtPrice,
      imageUrl: product.imageUrl || '/assets/images/product-placeholder.svg',
      imageAlt: product.imageAlt || product.name,
      category: product.category,
      categorySlug: product.categorySlug,
      material: product.material,
      featured: product.featured,
      active: true,
      onSale: product.onSale,
      availableSizes: product.availableSizes,
      availableColors: product.availableColors,
      stock: product.availableStock
    };
  }

  private fromDetail(product: ProductDetailApi): Product {
    const sortedImages = [...product.images].sort((a, b) => {
      if (a.mainImage !== b.mainImage) return a.mainImage ? -1 : 1;
      return a.displayOrder - b.displayOrder;
    });
    const activeVariants = product.variants.filter(variant => variant.active);

    return {
      id: product.id,
      name: product.name,
      slug: product.slug,
      description: product.description,
      shortDescription: product.shortDescription,
      price: product.price,
      originalPrice: product.compareAtPrice,
      imageUrl: sortedImages[0]?.url || '/assets/images/product-placeholder.svg',
      imageAlt: sortedImages[0]?.altText || product.name,
      category: product.category,
      categorySlug: product.categorySlug,
      material: product.material,
      brand: product.brand,
      featured: product.featured,
      active: true,
      onSale: product.compareAtPrice != null && product.compareAtPrice > product.price,
      availableSizes: [...new Set(activeVariants.filter(v => v.availableStock > 0).map(v => v.size))].sort(),
      availableColors: [...new Set(activeVariants.filter(v => v.availableStock > 0).map(v => v.color))].sort(),
      stock: activeVariants.reduce((sum, variant) => sum + variant.availableStock, 0),
      variants: activeVariants,
      images: sortedImages
    };
  }
}
