import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface AdminDashboard {
  totalProducts: number;
  activeProducts: number;
  lowStockVariants: number;
  totalOrders: number;
  pendingOrders: number;
  approvedRevenue: number;
}

export interface AdminVariant {
  id: number;
  sku: string;
  size: number;
  color: string;
  stock: number;
  reserved: number;
  available: number;
  active: boolean;
}

export interface AdminProduct {
  id: number;
  name: string;
  slug: string;
  category: string;
  price: number;
  active: boolean;
  featured: boolean;
  variants: number;
  totalStock: number;
  reservedStock: number;
  inventory: AdminVariant[];
}

export interface AdminOrder {
  orderNumber: string;
  customerName: string;
  customerEmail: string;
  status: string;
  paymentStatus: string;
  total: number;
  createdAt: string;
}

export interface AdminProductRequest {
  name: string;
  slug: string;
  description: string;
  shortDescription?: string;
  price: number;
  categoryId: number;
  featured: boolean;
  material?: string;
  brand?: string;
  imageUrl: string;
  variants: Array<{ sku: string; size: number; color: string; stock: number }>;
}

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/admin';

  dashboard(): Observable<AdminDashboard> {
    return this.http.get<AdminDashboard>(`${this.apiUrl}/dashboard`);
  }

  products(): Observable<AdminProduct[]> {
    return this.http.get<AdminProduct[]>(`${this.apiUrl}/products`);
  }

  createProduct(request: AdminProductRequest): Observable<AdminProduct> {
    return this.http.post<AdminProduct>(`${this.apiUrl}/products`, request);
  }

  toggleProduct(productId: number, active: boolean): Observable<AdminProduct> {
    return this.http.patch<AdminProduct>(
      `${this.apiUrl}/products/${productId}/active`,
      { active }
    );
  }

  adjustStock(variantId: number, stockQuantity: number, reason: string): Observable<void> {
    return this.http.patch<void>(`${this.apiUrl}/variants/${variantId}/stock`, {
      stockQuantity,
      reason
    });
  }

  orders(): Observable<AdminOrder[]> {
    return this.http.get<AdminOrder[]>(`${this.apiUrl}/orders`);
  }

  advanceOrder(
    orderNumber: string,
    status: string,
    trackingCode?: string
  ): Observable<AdminOrder> {
    return this.http.patch<AdminOrder>(
      `${this.apiUrl}/orders/${orderNumber}/status`,
      { status, trackingCode }
    );
  }
}
