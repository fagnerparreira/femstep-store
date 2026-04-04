import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Product } from '../models/product.model';

@Injectable({
  providedIn: 'root'
})
export class ProductService {
  constructor() { }

  getProducts(filters?: {
    category?: string;
    minPrice?: number;
    maxPrice?: number;
    size?: number;
    color?: string;
    featured?: boolean;
    active?: boolean;
  }): Observable<Product[]> {
    // TODO: Implement actual API call
    return this.getMockProducts(filters);
  }

  getProductById(id: number): Observable<Product | null> {
    // TODO: Implement actual API call
    return new Observable(observer => {
      setTimeout(() => {
        const products = this.getMockProductArray();
        const product = products.find(p => p.id === id) || null;
        observer.next(product);
        observer.complete();
      }, 500);
    });
  }

  getFeaturedProducts(): Observable<Product[]> {
    return this.getProducts({ featured: true });
  }

  getProductsByCategory(categorySlug: string): Observable<Product[]> {
    return this.getProducts({ category: categorySlug });
  }

  searchProducts(query: string): Observable<Product[]> {
    // TODO: Implement actual API call
    return new Observable(observer => {
      setTimeout(() => {
        const products = this.getMockProductArray();
        const filtered = products.filter(p => 
          p.name.toLowerCase().includes(query.toLowerCase()) ||
          p.description.toLowerCase().includes(query.toLowerCase())
        );
        observer.next(filtered);
        observer.complete();
      }, 500);
    });
  }

  private getMockProducts(filters?: any): Observable<Product[]> {
    return new Observable(observer => {
      setTimeout(() => {
        let products = this.getMockProductArray();

        if (filters) {
          if (filters.category) {
            products = products.filter(p => p.categorySlug === filters.category);
          }
          if (filters.minPrice) {
            products = products.filter(p => p.price >= filters.minPrice);
          }
          if (filters.maxPrice) {
            products = products.filter(p => p.price <= filters.maxPrice);
          }
          if (filters.featured) {
            products = products.filter(p => p.featured);
          }
          if (filters.active !== undefined) {
            products = products.filter(p => p.active === filters.active);
          }
        }

        observer.next(products);
        observer.complete();
      }, 500);
    });
  }

  private getMockProductArray(): Product[] {
    return [
      {
        id: 1,
        name: 'Salto Alto Elegante',
        slug: 'salto-alto-elegante',
        description: 'Salto alto elegante perfeito para ocasiões especiais. Confortável e sofisticado.',
        price: 299.90,
        originalPrice: 399.90,
        imageUrl: '/assets/images/products/salto1.jpg',
        category: 'Saltos',
        categorySlug: 'saltos',
        featured: true,
        active: true,
        isNew: true,
        onSale: true,
        availableSizes: [33, 34, 35, 36, 37, 38],
        availableColors: ['Preto', 'Nude', 'Vermelho'],
        stock: 15
      },
      {
        id: 2,
        name: 'Sapatilha Casual',
        slug: 'sapatilha-casual',
        description: 'Sapatilha confortável para o dia a dia. Versátil e estilosa.',
        price: 159.90,
        imageUrl: '/assets/images/products/sapatilha1.jpg',
        category: 'Sapatilhas',
        categorySlug: 'sapatilhas',
        featured: true,
        active: true,
        isNew: false,
        onSale: false,
        availableSizes: [33, 34, 35, 36, 37, 38, 39, 40],
        availableColors: ['Bege', 'Marrom', 'Preto'],
        stock: 25
      },
      {
        id: 3,
        name: 'Bota Cano Curto',
        slug: 'bota-cano-curto',
        description: 'Bota moderna com cano curto, ideal para o inverno.',
        price: 249.90,
        imageUrl: '/assets/images/products/bota1.jpg',
        category: 'Botas',
        categorySlug: 'botas',
        featured: false,
        active: true,
        isNew: false,
        onSale: false,
        availableSizes: [34, 35, 36, 37, 38, 39],
        availableColors: ['Preto', 'Marrom'],
        stock: 12
      }
    ];
  }
}
