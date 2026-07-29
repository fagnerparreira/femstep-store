import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';
import { Product } from '../models/product.model';
import { ProductService, ProductSummaryApi } from './product.service';

@Injectable({ providedIn: 'root' })
export class FavoriteService {
  private readonly apiUrl = '/api/account/favorites';

  constructor(
    private readonly http: HttpClient,
    private readonly productService: ProductService
  ) {}

  list(): Observable<Product[]> {
    return this.http.get<ProductSummaryApi[]>(this.apiUrl).pipe(
      map(products => products.map(product => this.productService.fromSummary(product)))
    );
  }

  contains(productId: number): Observable<boolean> {
    return this.http
      .get<{ favorite: boolean }>(`${this.apiUrl}/${productId}`)
      .pipe(map(response => response.favorite));
  }

  add(productId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/${productId}`, {});
  }

  remove(productId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${productId}`);
  }
}
